FROM amazoncorretto:25-al2023@sha256:d4b9047349107b5c5a3c67f89853c48a83d4c769d4196a03f675a4c365737f7b AS jdk
FROM node:24.22.3-bookworm@sha256:49f7adaf9f1574bc45c20ea317139b39a59d7e758d0e93745e112fd85cb42623 AS node
FROM ubuntu:24.04@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git openssh-client python3 flac procps unzip \
    && rm -rf /var/lib/apt/lists/*
COPY --from=jdk /usr/lib/jvm/java-25-amazon-corretto /opt/java
RUN rm /opt/java/lib/security/cacerts
COPY --from=jdk /etc/pki/ca-trust/extracted/java/cacerts /opt/java/lib/security/cacerts
COPY backend/security /tmp/java-security
RUN sh /tmp/java-security/update-java-blocklist.sh /opt/java
COPY --from=node /usr/local /usr/local
ENV JAVA_HOME=/opt/java PATH=/opt/java/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
COPY scripts/install-jj.sh /usr/local/lib/abservice/install-jj.sh
RUN npm install --global npm@11.19.1 --registry=https://npm.flatt.tech/ \
    && bash /usr/local/lib/abservice/install-jj.sh /usr/local/bin \
    && java -version && node --version && npm --version
# Ubuntu's default uid 1000 is used; no sudo, Docker socket or host credentials.
RUN usermod -l developer -d /home/developer -m ubuntu
ENV HOME=/home/developer ASTRO_TELEMETRY_DISABLED=1 GRADLE_USER_HOME=/home/developer/.gradle
USER developer
CMD ["sleep", "infinity"]
