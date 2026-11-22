FROM golang:1.27.4-bookworm@sha256:4fa7c968ba93f1635670522dbb01a2c78150443e1385746924566079f36570ea AS gosu-build
WORKDIR /src
RUN git init . \
    && git remote add origin https://github.com/tianon/gosu.git \
    && git fetch --depth=1 origin 29b939aa5bd4dc588af765b8248f6e39150d2565 \
    && git checkout --detach 29b939aa5bd4dc588af765b8248f6e39150d2565
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
RUN go get golang.org/x/sys@v0.50.0 \
    && go mod download && go mod verify \
    && go build -trimpath -o /out/gosu .

# Preserve PostgreSQL 15.20 and its official entrypoint/data layout.
FROM postgres:15.20-alpine3.24@sha256:e18480a22d6e48f4d816a6696769b1ec09ac73a824c5649e49c7d8b684913f3d
RUN apk upgrade --no-cache \
    && test "$(apk version -t "$(apk info -v libxml2 | sed 's/^libxml2-//')" 2.13.9-r3)" != '<' \
    && test "$(apk version -t "$(apk info -v libssl3 | sed 's/^libssl3-//')" 3.5.9-r1)" != '<'
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
RUN gosu nobody true && postgres --version
