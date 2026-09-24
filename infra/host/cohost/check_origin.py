"""Exercise rendered HTTP/TLS boundaries with real Nginx and disposable fixtures."""
import http.client
import json
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import time
import uuid

from origin import render


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=180).stdout.strip()


def check():
    domain = "origin.example.invalid"
    for invalid in ("a;return 200", "a\nb", "*.example.invalid", "127.0.0.1", "https://example.invalid", "example.invalid/", "-a.example.invalid"):
        try:
            render(invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("Accepted unsafe domain")
    container = "origin-check-" + uuid.uuid4().hex[:10]
    image = "nginx@sha256:30f1c0d78e0ad60901648be663a710bdadf19e4c10ac6782c235200619158284"
    with tempfile.TemporaryDirectory(prefix="origin-check-") as temp:
        root = Path(temp)
        root.chmod(0o755)
        challenge = root/".well-known/acme-challenge"
        challenge.mkdir(parents=True)
        (challenge/"test-token").write_text("public-acme-fixture")
        run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
            "-subj", "/CN="+domain, "-addext", "subjectAltName=DNS:"+domain,
            "-keyout", str(root/"privkey.pem"), "-out", str(root/"fullchain.pem"))
        # A fixture backend exposes /q/ deliberately, to detect proxy bypasses.
        backend = """server { listen 8080; server_name _;
            location /api/ {
                if ($http_x_origin_verify != deliberately-invalid-test-token) { return 403; }
                return 200 'accepted';
            }
            location /q/ { return 200 'management-must-not-escape'; }
        }
"""
        for tls in (False, True):
            conf = render(domain, tls)["nginx.conf"]
            conf = conf.replace("/var/log/abservice-origin/error.log", "/tmp/error.log")
            conf = conf.replace("/var/log/abservice-origin/access.log", "/tmp/access.log")
            conf = conf.replace("/var/lib/abservice/acme", "/fixture")
            conf = conf.replace("/etc/letsencrypt/live/"+domain, "/fixture")
            conf = conf.rsplit("}", 1)[0] + backend + "}\n"
            (root/"nginx.conf").write_text(conf)
            try:
                run("docker", "run", "-d", "--name", container, "-p", "127.0.0.1::80", "-p", "127.0.0.1::443",
                    "-v", temp+":/fixture:ro", image, "nginx", "-c", "/fixture/nginx.conf", "-g", "daemon off;")
                ports = json.loads(run("docker", "inspect", container))[0]["NetworkSettings"]["Ports"]
                http_port = int(ports["80/tcp"][0]["HostPort"])
                tls_port = int(ports["443/tcp"][0]["HostPort"])
                context = ssl.create_default_context(cafile=str(root/"fullchain.pem"))
                context.minimum_version = ssl.TLSVersion.TLSv1_2

                def request(path, secure=False, host=domain, token=False, method="GET", sni=domain):
                    conn = http.client.HTTPConnection("127.0.0.1", tls_port if secure else http_port, timeout=5)
                    if secure:
                        conn.sock = context.wrap_socket(socket.create_connection(("127.0.0.1", tls_port), 5), server_hostname=sni)
                    headers = {"Host": host}
                    if token:
                        headers["X-Origin-Verify"] = "deliberately-invalid-test-token"
                    try:
                        conn.request(method, path, headers=headers)
                        response = conn.getresponse()
                        return response.status, response.read()
                    finally:
                        conn.close()

                for attempt in range(20):
                    try:
                        assert request("/")[0] == 404
                        break
                    except (OSError, http.client.HTTPException):
                        if attempt == 19:
                            raise
                        time.sleep(0.2)
                assert request("/.well-known/acme-challenge/test-token") == (200, b"public-acme-fixture")
                assert request("/.well-known/acme-challenge/test-token", method="POST")[0] == 403
                assert request("/.well-known/acme-challenge/test-token", host="other.invalid")[0] == 404
                assert request("/.well-known/acme-challenge/missing")[0] == 404
                for path in ("/api/v1/site-contents", "/q/health", "/", "/.well-known/acme-challenge/../privkey.pem"):
                    assert request(path, token=True)[0] == 404
                if tls:
                    assert request("/api/v1/site-contents", secure=True, token=True)[0] == 200
                    assert request("/api/v1/site-contents", secure=True)[0] == 403
                    for path in ("/q/health", "/", "/api/../q/health", "/api/%2e%2e/q/health"):
                        assert request(path, secure=True, token=True)[0] == 404
                    try:
                        request("/api/v1/site-contents", secure=True, token=True, host="other.invalid")
                    except (OSError, http.client.HTTPException):
                        pass
                    else:
                        raise AssertionError("Accepted wrong Host")
                    try:
                        request("/api/v1/site-contents", secure=True, token=True, sni="other.invalid")
                    except ssl.SSLError:
                        pass
                    else:
                        raise AssertionError("Accepted wrong SNI")
            finally:
                subprocess.run(["docker", "rm", "-f", container], capture_output=True, timeout=20)
        print(json.dumps({"httpChallengeOnly": True, "tlsApiOnly": True, "tokenPreserved": True,
                          "managementBypassRejected": True, "hostAndSniRejected": True,
                          "image": json.loads(run("docker", "image", "inspect", image))[0]["RepoDigests"]}))


if __name__ == "__main__":
    check()
