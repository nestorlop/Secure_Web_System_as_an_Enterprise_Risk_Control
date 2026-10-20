#!/usr/bin/env bash
# Ubuntu, dedicated server. Run only after building with REAL Auth0 settings.
set -euo pipefail
if [[ $EUID -ne 0 ]]; then echo 'Run with sudo.' >&2; exit 1; fi
if [[ $# -ne 2 ]]; then echo 'Usage: sudo bash deploy/install.sh SUBDOMAIN.duckdns.org https://TENANT.us.auth0.com/' >&2; exit 1; fi
domain=$1
issuer=$2
[[ $domain =~ ^[a-z0-9][a-z0-9-]*\.duckdns\.org$ && $domain != YOUR* ]] || { echo 'Invalid Duck DNS hostname' >&2; exit 1; }
[[ $issuer =~ ^https://[a-zA-Z0-9.-]+/$ && $issuer != *YOUR* ]] || { echo 'Issuer must use HTTPS and end with /' >&2; exit 1; }
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
jar="$root/backend/target/secure_web_system_as_an_enterprise_risk_control-0.0.1-SNAPSHOT.jar"
[[ -f $jar && -f "$root/frontend/dist/index.html" ]] || { echo 'Build backend and frontend first.' >&2; exit 1; }
# Refuse accidental replacement of an existing installation. Follow README for updates.
for target in /etc/enterprise-risk-api.env /etc/apache2/sites-available/enterprise-risk.conf /opt/enterprise-risk-api/app.jar /var/www/enterprise-risk-frontend/index.html; do
    [[ ! -e $target ]] || { echo "Existing installation: $target. Review before updating." >&2; exit 1; }
done
apt-get update
apt-get install -y apache2 certbot python3-certbot-apache openjdk-21-jre-headless curl
id riskapi >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin riskapi
install -d -o root -g riskapi -m 0750 /opt/enterprise-risk-api
install -o root -g riskapi -m 0640 "$jar" /opt/enterprise-risk-api/app.jar
printf 'AUTH0_ISSUER_URI=%s\nAUTH0_AUDIENCE=https://enterprise-risk-api\nCORS_ALLOWED_ORIGINS=https://%s\n' "$issuer" "$domain" > /etc/enterprise-risk-api.env
chmod 0600 /etc/enterprise-risk-api.env
install -m 0644 "$root/deploy/enterprise-risk-api.service" /etc/systemd/system/enterprise-risk-api.service
install -d -m 0755 /var/www/enterprise-risk-frontend
cp -R "$root/frontend/dist/." /var/www/enterprise-risk-frontend/
find /var/www/enterprise-risk-frontend -type d -exec chmod 0755 {} +
find /var/www/enterprise-risk-frontend -type f -exec chmod 0644 {} +
sed "s/YOUR-SUBDOMAIN.duckdns.org/$domain/g" "$root/deploy/enterprise-risk.conf.template" > /etc/apache2/sites-available/enterprise-risk.conf
a2enmod ssl proxy proxy_http rewrite headers
a2ensite enterprise-risk.conf
apachectl configtest
systemctl daemon-reload
systemctl enable --now enterprise-risk-api
systemctl reload apache2
echo "HTTP setup prepared. Verify service health and DNS, then run:"
echo "sudo certbot --apache --redirect -d $domain"
echo 'sudo certbot renew --dry-run'
echo 'Do not log in over public HTTP. Finish HTTPS first.'
