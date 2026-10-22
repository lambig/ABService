# MinIO's prebuilt registry images are no longer available. Build official,
# unmodified source at fixed release commits for local development and CI only.
FROM golang:1.26.10-bookworm@sha256:632dff7351799a5a9954a48dc06670ade72ee44e8c351cdc0e5a57af2fea21af AS builder
RUN apt-get -o APT::Update::Error-Mode=any update \
    && apt-get install -y --only-upgrade ca-certificates \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' ca-certificates)" ge '20250419~deb12u2' \
    && rm -rf /var/lib/apt/lists/*
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOFLAGS=-mod=readonly
WORKDIR /src

FROM builder AS minio-build
# RELEASE.2025-10-15T17-29-55Z (includes GHSA-jjjj-jwhf-8rgr fix).
RUN git init . \
    && git remote add origin https://github.com/minio/minio.git \
    && git fetch --depth=1 origin 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a \
    && git checkout --detach 9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
RUN go mod download && go mod verify \
    && go build -trimpath -ldflags="$(go run buildscripts/gen-ldflags.go)" -o /out/minio .

FROM builder AS mc-build
# RELEASE.2025-08-13T08-35-41Z.
RUN git init . \
    && git remote add origin https://github.com/minio/mc.git \
    && git fetch --depth=1 origin 7394ce0dd2a80935aded936b09fa12cbb3cb8096 \
    && git checkout --detach 7394ce0dd2a80935aded936b09fa12cbb3cb8096
RUN go mod download && go mod verify \
    && go build -trimpath -ldflags="$(go run buildscripts/gen-ldflags.go)" -o /out/mc .

FROM debian:bookworm-slim@sha256:3783cc01769c7b2b1b83a5c5ad96c815348e28ed7da68e2e3687004faa906251 AS runtime
# curl is used by the existing Compose health check; sh by bucket bootstrap.
RUN apt-get -o APT::Update::Error-Mode=any update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' ca-certificates)" ge '20250419~deb12u2' \
    && rm -rf /var/lib/apt/lists/*

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
