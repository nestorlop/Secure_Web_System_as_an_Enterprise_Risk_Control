package co.edu.escuelaing.secure_web_system_as_an_enterprise_risk_control;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RiskController {
    private static final Logger log = LoggerFactory.getLogger(RiskController.class);
    @GetMapping("/risk-status")
    public Map<String, Object> riskStatus(@AuthenticationPrincipal Jwt jwt) {
        // No tokens, credentials, headers or personal identifiers in logs.
        log.info("event=risk_status_read outcome=success");
        return Map.of("status", "protected", "subject", jwt.getSubject(),
            "control", "JWT validation and authenticated access");
    }
}
