"""Check patched local MinIO rejects unsigned trailers and accepts signed CRUD."""
import argparse
import datetime
import hashlib
import hmac
import urllib.error
import urllib.parse
import urllib.request
import uuid


def request(endpoint, method, path, body=b"", unsigned=False, query_auth=False, extra_headers=None):
    now = datetime.datetime.now(datetime.timezone.utc)
    stamp, day = now.strftime("%Y%m%dT%H%M%SZ"), now.strftime("%Y%m%d")
    host = urllib.parse.urlsplit(endpoint).netloc
    payload_hash = "STREAMING-UNSIGNED-PAYLOAD-TRAILER" if unsigned else hashlib.sha256(body).hexdigest()
    headers = {"host": host, "x-amz-content-sha256": payload_hash, "x-amz-date": stamp}
    if unsigned:
        headers.update({"content-encoding": "aws-chunked", "x-amz-decoded-content-length": "1", "x-amz-trailer": "x-amz-checksum-crc32"})
        if "snowball" in path:
            headers["x-amz-meta-snowball-auto-extract"] = "true"
    headers.update(extra_headers or {})
    parts = urllib.parse.urlsplit(path)
    query = urllib.parse.urlencode(sorted(urllib.parse.parse_qsl(parts.query)))
    signed_headers = ";".join(sorted(headers))
    canonical = "\n".join([method, parts.path, query, "".join(k + ":" + headers[k] + "\n" for k in sorted(headers)), signed_headers, payload_hash])
    scope = day + "/us-east-1/s3/aws4_request"
    to_sign = "AWS4-HMAC-SHA256\n" + stamp + "\n" + scope + "\n" + hashlib.sha256(canonical.encode()).hexdigest()
    key = b"AWS4minioadmin123"  # Disposable local Compose fixture only.
    for value in [day, "us-east-1", "s3", "aws4_request"]:
        key = hmac.new(key, value.encode(), hashlib.sha256).digest()
    signature = "0" * 64 if unsigned else hmac.new(key, to_sign.encode(), hashlib.sha256).hexdigest()
    headers["Authorization"] = "AWS4-HMAC-SHA256 Credential=minioadmin/" + scope + ", SignedHeaders=" + signed_headers + ", Signature=" + signature
    if query_auth:
        del headers["Authorization"]
        path += ("&" if "?" in path else "?") + urllib.parse.urlencode({"X-Amz-Credential": "minioadmin/" + scope})
    req = urllib.request.Request(endpoint + path, data=body if method in ("PUT", "POST") else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def main(endpoint):
    parsed = urllib.parse.urlsplit(endpoint)
    if parsed.scheme != "http" or parsed.hostname not in ("localhost", "127.0.0.1"):
        raise ValueError("Use the disposable localhost MinIO instance")
    bucket = "/auth-regression-" + uuid.uuid4().hex
    assert request(endpoint, "PUT", bucket)[0] == 200
    try:
        for query_auth in (False, True):
            for path in [bucket + "/forged", bucket + "/part?partNumber=1&uploadId=fixture", bucket + "/snowball.tar"]:
                status, body = request(endpoint, "PUT", path, b"1\r\nx\r\n0\r\nx-amz-checksum-crc32:AAAAAA==\r\n\r\n", unsigned=True, query_auth=query_auth)
                assert status == 400 and b"<Code>InvalidRequest</Code>" in body and b"authorization mechanism" in body, (status, body)
        status, body = request(endpoint, "PUT", bucket + "/replication", b"fixture", extra_headers={"x-minio-replication-server-side-encryption-sealed-key": "invalid-fixture"})
        assert status == 501 and b"<Code>NotImplemented</Code>" in body, (status, body)
        content = b"signed fixture bytes"
        assert request(endpoint, "PUT", bucket + "/valid", content)[0] == 200
        assert request(endpoint, "GET", bucket + "/valid") == (200, content)
        status, body = request(endpoint, "POST", bucket + "/valid?select&select-type=2", b"<SelectObjectContentRequest/>")
        assert status == 501 and b"<Code>NotImplemented</Code>" in body, (status, body)
        assert request(endpoint, "DELETE", bucket + "/valid")[0] == 204
    finally:
        assert request(endpoint, "DELETE", bucket)[0] == 204
    print("PASS: six unsigned PUT variants, replication headers and S3 Select rejected; signed bucket/object CRUD preserved")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", default="http://localhost:9000")
    main(parser.parse_args().endpoint.rstrip("/"))
