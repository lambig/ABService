"""Render opt-in Nginx/Certbot units. Installation and certificate issuance are explicit."""
import argparse
from pathlib import Path
import re


def render(domain, tls=False):
    if len(domain) > 253 or not re.fullmatch(
        r"(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?", domain
    ):
        raise ValueError("Use one lowercase DNS name, without scheme, wildcard, port or path")
    conf = r"""worker_processes auto;
pid /run/abservice-origin.pid;
error_log /var/log/abservice-origin/error.log crit;
events { worker_connections 512; }
http {
    server_tokens off;
    log_format bounded '$time_iso8601 $request_method $status';
    access_log /var/log/abservice-origin/access.log bounded;
    client_max_body_size 1m;
    client_body_timeout 15s;
    client_header_timeout 15s;
    keepalive_timeout 30s;
    server {
        listen 80 default_server;
        server_name _;
        return 404;
    }
    server {
        listen 80;
        server_name DOMAIN;
        location ~ ^/\.well-known/acme-challenge/[A-Za-z0-9_-]+$ {
            root /var/lib/abservice/acme;
            default_type text/plain;
            limit_except GET { deny all; }
            try_files $uri =404;
        }
        location / { return 404; }
    }
""".replace("DOMAIN", domain)
    if tls:
        conf += """    server {
        listen 443 ssl default_server;
        server_name _;
        ssl_reject_handshake on;
        return 444;
    }
    server {
        listen 443 ssl;
        server_name DOMAIN;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_certificate /etc/letsencrypt/live/DOMAIN/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/DOMAIN/privkey.pem;
        if ($ssl_server_name != DOMAIN) { return 421; }
        location /api/ {
            proxy_pass http://127.0.0.1:8080/api/;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_set_header Host $host;
            proxy_set_header X-Forwarded-Proto https;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_connect_timeout 5s;
            proxy_read_timeout 60s;
            # Preserve X-Origin-Verify; the application validates it.
            # Never inject the expected token here: other distributions share IPs.
        }
        location / { return 404; }
    }
""".replace("DOMAIN", domain)
    conf += "}\n"
    files = {"nginx.conf": conf, "abservice-origin.service": """[Unit]
Description=Restricted API origin and ACME HTTP challenge
After=network-online.target docker.service
Wants=network-online.target
[Service]
Type=forking
PIDFile=/run/abservice-origin.pid
ExecStartPre=/usr/sbin/nginx -t -c /etc/abservice/origin/nginx.conf
ExecStart=/usr/sbin/nginx -c /etc/abservice/origin/nginx.conf
ExecReload=/usr/sbin/nginx -t -c /etc/abservice/origin/nginx.conf
ExecReload=/bin/kill -HUP $MAINPID
KillSignal=SIGQUIT
TimeoutStopSec=30
Restart=on-failure
[Install]
WantedBy=multi-user.target
"""}
    files["abservice-origin.logrotate"] = """/var/log/abservice-origin/*.log {
    daily
    maxsize 10M
    rotate 7
    missingok
    notifempty
    compress
    delaycompress
    create 0640 root root
    sharedscripts
    postrotate
        /usr/bin/systemctl reload abservice-origin.service >/dev/null 2>&1 || true
    endscript
}
"""
    if tls:
        files["abservice-origin-renew.service"] = """[Unit]
Description=Renew the API origin certificate
After=network-online.target abservice-origin.service
Wants=network-online.target
[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --cert-name DOMAIN --quiet --deploy-hook "/usr/bin/systemctl reload abservice-origin.service"
TimeoutStartSec=15min
""".replace("DOMAIN", domain)
        files["abservice-origin-renew.timer"] = """[Unit]
Description=Check API origin certificate renewal twice daily
[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=3600
Persistent=true
[Install]
WantedBy=timers.target
"""
    return files


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--domain", required=True)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--tls", action="store_true")
    args = parser.parse_args()
    files = render(args.domain, args.tls)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, content in files.items():
        (args.output_dir / name).write_text(content)
