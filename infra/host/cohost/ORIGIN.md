# Optional HTTPS API origin

`origin.py` renders a dedicated Nginx configuration and systemd units. It does not
install packages, modify firewall rules, issue certificates, start services or
change DNS. Keep real domain names, contact information and deployment records in
the private operations repository. The application stays on `127.0.0.1:8080`;
PostgreSQL remains inside the Docker network.

## Preparation and HTTP challenge

Install the distribution's Nginx, Certbot (webroot support), and logrotate packages.
Check for other listeners before installing the dedicated service; do not start
the package's default Nginx service alongside it. Render to a staging directory:

```sh
python3 origin.py --domain origin.example.invalid --output-dir /private/origin-stage
```

Create `/etc/abservice/origin`, `/var/lib/abservice/acme` and
`/var/log/abservice-origin`. The ACME webroot must be readable by the Nginx worker
and writable only by the certificate manager/root. Install `nginx.conf` at
`/etc/abservice/origin/nginx.conf`, the service under `/etc/systemd/system`, and
`abservice-origin.logrotate` under `/etc/logrotate.d/abservice-origin`.
Validate with `nginx -t -c /etc/abservice/origin/nginx.conf`, reload systemd and
enable/start `abservice-origin.service` while the public firewall is still closed.

Using loopback and the intended Host, verify a temporary file under
`/.well-known/acme-challenge/` can be read, and all application, management,
wrong-Host and other HTTP paths are rejected. Remove the temporary file.
Only then enable `origin_http_validation_enabled` in the independent host Terraform
root. Review the saved plan for just the expected port change; SSH stays `/32`.

Point the origin DNS name to the host and obtain a publicly trusted certificate
with Certbot `certonly --webroot -w /var/lib/abservice/acme --cert-name <domain>
-d <domain>`. Supply the operator's ACME registration preferences explicitly.
Keep the private key on the host under Certbot's protected directories. Test the
HTTP-01 route with the staging CA before the production request to avoid rate
limits. Public HTTP is required for this validation/renewal route, but it never
proxies the application. See [Let's Encrypt challenges](https://letsencrypt.org/docs/challenge-types/).

## TLS activation and renewal

Render again with `--tls` once `/etc/letsencrypt/live/<domain>/fullchain.pem` and
`privkey.pem` exist. Test the candidate configuration before replacing the live
one and reloading the service; preserve the last working configuration for rollback.
Install/enable `abservice-origin-renew.timer`. The timer checks twice daily with
up to an hour of jitter, and catches missed runs after reboot. Certbot's successful
renewal hook reloads this service; its ExecReload first validates the configuration.
Run `certbot renew --cert-name <domain> --dry-run --run-deploy-hooks` and check the
timer schedule, resulting certificate and service after the hook. Expiry and
renewal-failure notifications still require the operations monitoring system.

Before enabling `origin_https_enabled`, use a local TLS client with the real
Host/SNI and normal CA verification to check the API with the origin token,
missing/incorrect token rejection, management-path rejection, and wrong Host/SNI
rejection. Do not print the token or pass it on a command line. The proxy preserves
the incoming token; it must never insert the expected token for every caller.
The firewall permits CloudFront's shared origin-facing IPv4 ranges, so the
application's token check is still required to distinguish distributions.

HTTP-01 is the only public port-80 content. TLS proxies only `/api/` and rejects
other paths, including `/q/`. The TLS default server rejects unrecognized SNI.
The access log records time, method and status, excluding URL/query/header/body
secrets. Rotation is daily or at the size threshold when logrotate runs, with
seven retained rotations; configure host-wide disk monitoring and external log
delivery separately. Rotation is not a hard disk cap.

## Firewall refresh and acceptance

`infra/cohost-host` reads AWS's current `CLOUDFRONT_ORIGIN_FACING` IPv4 ranges on
each Terraform refresh. It fails on empty ranges or more than 60 combined IPv4
rules. No IPv6 access or application/database port is added. This is not a
continuously updating managed prefix list: arrange an operational check for range
changes, review/apply the updated firewall, and alert on failure or quota overflow.

Verify the real firewall and CDN-to-origin connection after apply. Then exercise
browser/admin/upload flows and canonical-domain TLS through the intended CDN.
Passing the local test does not mean deployment, renewal or release acceptance.
For rollback, close the optional public ports through the reviewed Terraform
configuration and restore the previous Nginx configuration; do not remove the
application state, certificates, or unrelated DNS records.

`python3 check_origin.py` runs real HTTP/TLS boundary tests in a disposable,
digest-pinned Nginx container. It uses a self-signed temporary certificate and
deliberately invalid token fixture, not production credentials. The target host's
package/configuration still needs its own validation before activation.
