FROM node:22-bookworm-slim AS build
WORKDIR /build
RUN npm install --global pnpm@11.25.0
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/index.html ./index.html
COPY frontend/src ./src
ARG VITE_AUTH0_DOMAIN
ARG VITE_AUTH0_CLIENT_ID
ARG VITE_AUTH0_AUDIENCE=https://enterprise-risk-api
ENV VITE_AUTH0_DOMAIN=$VITE_AUTH0_DOMAIN VITE_AUTH0_CLIENT_ID=$VITE_AUTH0_CLIENT_ID VITE_AUTH0_AUDIENCE=$VITE_AUTH0_AUDIENCE VITE_API_URL=""
RUN pnpm test && pnpm build

FROM httpd:2.4-bookworm
RUN sed -i \
    -e 's/^#LoadModule rewrite_module/LoadModule rewrite_module/' \
    -e 's/^#LoadModule proxy_module/LoadModule proxy_module/' \
    -e 's/^#LoadModule proxy_http_module/LoadModule proxy_http_module/' \
    -e 's/^#LoadModule headers_module/LoadModule headers_module/' \
    -e 's/^#LoadModule ssl_module/LoadModule ssl_module/' \
    -e 's/^#LoadModule socache_shmcb_module/LoadModule socache_shmcb_module/' \
    /usr/local/apache2/conf/httpd.conf \
    && printf '\nInclude conf/extra/enterprise-risk.conf\n' >> /usr/local/apache2/conf/httpd.conf
COPY --from=build /build/dist/ /usr/local/apache2/htdocs/
COPY deploy/docker/http-bootstrap.conf.template deploy/docker/https.conf.template /opt/risk-config/
COPY deploy/docker/start-apache.sh /usr/local/bin/start-apache.sh
RUN chmod 0755 /usr/local/bin/start-apache.sh
ENTRYPOINT ["/usr/local/bin/start-apache.sh"]
