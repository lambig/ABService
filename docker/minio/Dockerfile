# Build MinIO from official source
# at fixed release commits with the reviewed unsigned-trailer mitigation.
# These images are for local development and CI only.
FROM golang:1.27.4-bookworm@sha256:4fa7c968ba93f1635670522dbb01a2c78150443e1385746924566079f36570ea AS builder
RUN apt-get -o APT::Update::Error-Mode=any update \
    && apt-get install -y --only-upgrade ca-certificates \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' ca-certificates)" ge '20250419~deb12u2' \
    && rm -rf /var/lib/apt/lists/*
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOFLAGS=-mod=readonly
WORKDIR /src

FROM builder AS minio-build
COPY reject-unsigned-trailers.patch /tmp/reject-unsigned-trailers.patch
# RELEASE.2025-10-15T17-29-55Z (includes GHSA-jjjj-jwhf-8rgr fix).
RUN git init . \
    && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth=1 origin 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a \
    && git checkout --detach 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a \
    && git apply --check /tmp/reject-unsigned-trailers.patch \
    && git apply /tmp/reject-unsigned-trailers.patch
RUN go get filippo.io/edwards25519@v1.1.1 github.com/Azure/go-ntlmssp@v0.1.1 github.com/apache/thrift@v0.24.0 github.com/buger/jsonparser@v1.1.2 github.com/eclipse/paho.mqtt.golang@v1.5.1 github.com/go-jose/go-jose/v4@v4.1.6 github.com/klauspost/compress@v1.18.7 github.com/prometheus/prometheus@v0.311.3 github.com/rabbitmq/amqp091-go@v1.14.0 go.mongodb.org/mongo-driver@v1.17.7 go.opentelemetry.io/otel/sdk@v1.47.0 go.opentelemetry.io/otel/trace@v1.47.0 golang.org/x/crypto@v0.59.0 golang.org/x/net@v0.62.0 golang.org/x/sys@v0.50.0 golang.org/x/text@v0.44.0 google.golang.org/grpc@v1.83.2 \
    && go mod download && go mod verify \
    && go build -mod=mod -trimpath -ldflags="$(go run buildscripts/gen-ldflags.go)" -o /out/minio .

FROM builder AS mc-build
# RELEASE.2025-08-13T08-35-41Z.
RUN git init . \
    && git remote add origin https://github.com/minio/mc.git \
    && git fetch --depth=1 origin 7394ce0dd2a80935aded936b09fa12cbb3cb8096 \
    && git checkout --detach 7394ce0dd2a80935aded936b09fa12cbb3cb8096
RUN go get github.com/klauspost/compress@v1.18.7 github.com/prometheus/prometheus@v0.311.3 golang.org/x/crypto@v0.59.0 golang.org/x/net@v0.62.0 golang.org/x/sys@v0.50.0 golang.org/x/text@v0.44.0 google.golang.org/grpc@v1.83.2 \
    && go mod download && go mod verify \
    && go build -mod=mod -trimpath -ldflags="$(go run buildscripts/gen-ldflags.go)" -o /out/mc .

FROM alpine:3.24.4@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS runtime
# curl serves the Compose health check; the shell runs bucket bootstrap.
RUN apk upgrade --no-cache && apk add --no-cache ca-certificates curl

FROM runtime AS minio
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"
COPY --from=minio-build /out/minio /usr/local/bin/minio
COPY --from=minio-build /src/LICENSE /usr/share/licenses/minio/LICENSE
ENTRYPOINT ["minio"]

FROM runtime AS mc
LABEL org.opencontainers.image.source="https://github.com/minio/mc" \
      org.opencontainers.image.revision="7394ce0dd2a80935aded936b09fa12cbb3cb8096" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"
COPY --from=mc-build /out/mc /usr/local/bin/mc
COPY --from=mc-build /src/LICENSE /usr/share/licenses/mc/LICENSE
ENTRYPOINT ["mc"]
