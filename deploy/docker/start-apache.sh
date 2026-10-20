#!/bin/sh
set -eu
case "$PUBLIC_HOSTNAME" in
  ''|*[!a-z0-9.-]*) echo 'Invalid PUBLIC_HOSTNAME' >&2; exit 1 ;;
esac
if [ -s "/etc/letsencrypt/live/$PUBLIC_HOSTNAME/fullchain.pem" ] && [ -s "/etc/letsencrypt/live/$PUBLIC_HOSTNAME/privkey.pem" ]; then
    template=/opt/risk-config/https.conf.template
else
    template=/opt/risk-config/http-bootstrap.conf.template
fi
sed "s/__HOSTNAME__/$PUBLIC_HOSTNAME/g" "$template" > /usr/local/apache2/conf/extra/enterprise-risk.conf
httpd -t
exec httpd-foreground
