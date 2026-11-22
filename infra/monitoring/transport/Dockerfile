FROM golang:1.27.4-bookworm@sha256:4fa7c968ba93f1635670522dbb01a2c78150443e1385746924566079f36570ea AS builder
WORKDIR /src
RUN git init . \
    && git remote add origin https://github.com/aws/amazon-cloudwatch-agent.git \
    && git fetch --depth=1 origin f224b70c8b31c6a7af0058c2789e2aeba6d98ec2 \
    && git checkout --detach f224b70c8b31c6a7af0058c2789e2aeba6d98ec2
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOMAXPROCS=4
COPY otel-log-truncation.patch /tmp/otel-log-truncation.patch
COPY otel-log-truncation_test.go /tmp/otel-log-truncation_test.go
# Backport upstream 017b08c7f1018b619991aaf6b2299d4beb9da1d3 without changing the collector log API.
RUN go mod download go.opentelemetry.io/otel/sdk@v1.44.0 go.opentelemetry.io/otel/trace@v1.44.0 go.opentelemetry.io/otel/sdk/log@v0.19.0 \
    && mkdir /otel \
    && cp -a /go/pkg/mod/go.opentelemetry.io/otel/sdk@v1.44.0 /otel/sdk \
    && cp -a /go/pkg/mod/go.opentelemetry.io/otel/trace@v1.44.0 /otel/trace \
    && cp -a /go/pkg/mod/go.opentelemetry.io/otel/sdk/log@v0.19.0 /otel/sdk/log \
    && chmod -R u+w /otel \
    && cd /otel && git apply -p1 /tmp/otel-log-truncation.patch \
    && cp /tmp/otel-log-truncation_test.go sdk/log/security_test.go \
    && sed 's/package log/package trace/' /tmp/otel-log-truncation_test.go > sdk/trace/security_test.go \
    && cp sdk/trace/security_test.go trace/security_test.go
RUN go get github.com/prometheus/prometheus@v0.311.3 golang.org/x/crypto@v0.59.0 \
    && go mod edit -replace=go.opentelemetry.io/otel=go.opentelemetry.io/otel@v1.44.0 \
    && go mod edit -replace=go.opentelemetry.io/otel/metric=go.opentelemetry.io/otel/metric@v1.44.0 \
    && go mod edit -replace=go.opentelemetry.io/otel/sdk/metric=go.opentelemetry.io/otel/sdk/metric@v1.44.0 \
    && go mod edit -replace=go.opentelemetry.io/otel/log=go.opentelemetry.io/otel/log@v0.19.0 \
    && go mod edit -replace=go.opentelemetry.io/otel/sdk=/otel/sdk \
    && go mod edit -replace=go.opentelemetry.io/otel/trace=/otel/trace \
    && go mod edit -replace=go.opentelemetry.io/otel/sdk/log=/otel/sdk/log \
    && go mod download && go mod verify \
    && go test -mod=mod go.opentelemetry.io/otel/sdk/trace go.opentelemetry.io/otel/trace go.opentelemetry.io/otel/sdk/log -run TestReplacementRuneSecurityRegression
COPY otel-exporter-security.patch /tmp/otel-exporter-security.patch
# Keep the collector's log API while backporting TLS configuration and diagnostic redaction fixes.
# TLS: c65d435b43e5e6b82310e6b18dd4cdcb8ac63a0c; redaction: 3a1412d2b3bc4e4231fbeac2ed42117ae541bb38.
RUN go get google.golang.org/grpc@v1.83.2 github.com/apache/thrift@v0.24.0 \
    && mkdir -p /otel/exporters/otlp/otlplog \
    && cp -a "$(go list -m -f '{{.Dir}}' go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc)" /otel/exporters/otlp/otlplog/otlploggrpc \
    && cp -a "$(go list -m -f '{{.Dir}}' go.opentelemetry.io/otel/exporters/otlp/otlptrace)" /otel/exporters/otlp/otlptrace \
    && cp -a "$(go list -m -f '{{.Dir}}' go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc)" /otel/exporters/otlp/otlptrace/otlptracegrpc \
    && cp -a "$(go list -m -f '{{.Dir}}' go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp)" /otel/exporters/otlp/otlptrace/otlptracehttp \
    && chmod -R u+w /otel/exporters \
    && cd /otel && git apply -p1 /tmp/otel-exporter-security.patch \
    && cd /src && go mod edit -replace=go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc=/otel/exporters/otlp/otlplog/otlploggrpc \
    && go mod edit -replace=go.opentelemetry.io/otel/exporters/otlp/otlptrace=/otel/exporters/otlp/otlptrace \
    && go mod edit -replace=go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc=/otel/exporters/otlp/otlptrace/otlptracegrpc \
    && go mod edit -replace=go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp=/otel/exporters/otlp/otlptrace/otlptracehttp \
    && go mod verify
COPY otel-exporter-security_test.patch /tmp/otel-exporter-security_test.patch
RUN cd /otel && git apply -p1 /tmp/otel-exporter-security_test.patch \
    && cd /src && go test -mod=mod go.opentelemetry.io/otel/sdk/trace \
       go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc \
       go.opentelemetry.io/otel/exporters/otlp/otlptrace \
       go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc \
       go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp \
       -run 'TestEnvTLSCredentials|Test.*MarshalLogDoesNotInclude|TestTracerProviderCreatedLogDoesNotIncludeExporterConfig'
COPY prometheus-receiver.patch /tmp/prometheus-receiver.patch
# Match the released Prometheus 0.311.3 discovery metrics cleanup API.
RUN cp -a "$(go list -m -f '{{.Dir}}' github.com/open-telemetry/opentelemetry-collector-contrib/receiver/prometheusreceiver)" /patched-prometheusreceiver \
    && chmod -R u+w /patched-prometheusreceiver \
    && cd /patched-prometheusreceiver && git apply -p1 /tmp/prometheus-receiver.patch \
    && cd /src && go mod edit -replace=github.com/open-telemetry/opentelemetry-collector-contrib/receiver/prometheusreceiver=/patched-prometheusreceiver
RUN go build -mod=mod -p 4 -trimpath -ldflags='-s -w' -o /out/amazon-cloudwatch-agent ./cmd/amazon-cloudwatch-agent \
    && go build -mod=mod -p 4 -trimpath -ldflags='-s -w' -o /out/config-translator ./cmd/config-translator \
    && go build -mod=mod -p 4 -trimpath -ldflags='-s -w' -o /out/start-amazon-cloudwatch-agent ./cmd/start-amazon-cloudwatch-agent \
    && printf '%s\n' '1.300074.0-security-f224b70c8b31' > /out/CWAGENT_VERSION
FROM public.ecr.aws/cloudwatch-agent/cloudwatch-agent@sha256:982b213b6ce7ecbaa55d97c77075ca2badbb7a07e77f6979e1152fb0af3fc52b
COPY --from=builder /out/ /opt/aws/amazon-cloudwatch-agent/bin/
