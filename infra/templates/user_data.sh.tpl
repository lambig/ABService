#!/bin/bash
set -eu

dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

mkdir -p /opt/abservice

cat > /opt/abservice/docker-compose.prod.yml <<'COMPOSE_EOF'
${docker_compose_prod_yml}
COMPOSE_EOF

cat > /opt/abservice/deploy.sh <<'DEPLOY_EOF'
${deploy_sh}
DEPLOY_EOF
chmod +x /opt/abservice/deploy.sh
