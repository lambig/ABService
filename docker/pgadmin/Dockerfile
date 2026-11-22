FROM dpage/pgadmin4@sha256:3591b3d02910dcd817dec6fcaa681c278ba6966945283dd0ea0042731c330c83
USER root
RUN apk upgrade --no-cache \
    && python3 -m pip --python /venv/bin/python install --no-cache-dir \
       dnspython==2.9.2 msgpack==1.2.3 oauthlib==4.0.1 \
       opentelemetry-api==1.45.2 paramiko==5.0.0 pyasn1==0.6.5 \
       setuptools==84.1.0 urllib3==2.8.0 \
    && python3 -m pip --python /venv/bin/python check \
    && python3 -m pip install --no-cache-dir msgpack==1.2.3 setuptools==84.1.0 urllib3==2.8.0 \
    && python3 -m pip check \
    && python3 -m pip uninstall -y pip \
    && rm -rf /usr/local/lib/python3.15/ensurepip
USER pgadmin
