package co.edu.escuelaing.secure_web_system_as_an_enterprise_risk_control;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP and RSA signatures through the production decoder. Auth0 is replaced only in tests. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecureWebSystemAsAnEnterpriseRiskControlApplicationTests {
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static final String ISSUER;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-only").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/.well-known/jwks.json", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWKS.start();
            ISSUER = "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/";
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry props) {
        props.add("AUTH0_ISSUER_URI", () -> ISSUER);
        props.add("AUTH0_AUDIENCE", () -> "https://enterprise-risk-api");
        props.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:5173");
    }
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    @AfterAll static void stopJwks() { JWKS.stop(0); }
    String token(String issuer, String audience, Instant expiry, RSAKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("test-user")
            .audience(audience).issueTime(Date.from(Instant.now().minusSeconds(300)))
            .expirationTime(Date.from(expiry)).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
            .type(JOSEObjectType.JWT).keyID(KEY.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    HttpResponse<String> get(String path, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void missingTokenIs401() throws Exception {
        var response = get("/api/risk-status", null);
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"));
    }
    @Test void validTokenIs200() throws Exception {
        var response = get("/api/risk-status", token(ISSUER, "https://enterprise-risk-api", Instant.now().plusSeconds(300), KEY));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"protected\""));
        assertTrue(response.body().contains("\"subject\":\"test-user\""));
    }
    @Test void wrongAudienceIs401() throws Exception {
        assertEquals(401, get("/api/risk-status", token(ISSUER, "https://another-api", Instant.now().plusSeconds(300), KEY)).statusCode());
    }
    @Test void wrongIssuerIs401() throws Exception {
        assertEquals(401, get("/api/risk-status", token("https://another-issuer/", "https://enterprise-risk-api", Instant.now().plusSeconds(300), KEY)).statusCode());
    }
    @Test void expiredTokenIs401() throws Exception {
        assertEquals(401, get("/api/risk-status", token(ISSUER, "https://enterprise-risk-api", Instant.now().minusSeconds(120), KEY)).statusCode());
    }
    @Test void forgedSignatureIs401() throws Exception {
        var attacker = new RSAKeyGenerator(2048).generate();
        assertEquals(401, get("/api/risk-status", token(ISSUER, "https://enterprise-risk-api", Instant.now().plusSeconds(300), attacker)).statusCode());
    }
    @Test void malformedTokenIs401() throws Exception {
        assertEquals(401, get("/api/risk-status", "not-a-jwt").statusCode());
    }
    @Test void healthIsPublic() throws Exception {
        assertEquals(200, get("/actuator/health", null).statusCode());
    }
    HttpResponse<String> preflight(String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/risk-status"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", origin)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "authorization").build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void configuredCorsOriginIsAllowed() throws Exception {
        var response = preflight("http://localhost:5173");
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }
    @Test void unrelatedCorsOriginIsRejected() throws Exception {
        var response = preflight("https://untrusted.example");
        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }
    @Test void otherEndpointsDenied() throws Exception {
        var bearer = token(ISSUER, "https://enterprise-risk-api", Instant.now().plusSeconds(300), KEY);
        assertEquals(403, get("/actuator/env", bearer).statusCode());
    }
}
