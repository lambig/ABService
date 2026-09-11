#!/bin/bash
set -eu

dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

mkdir -p /opt/abservice

# ここが置くのは、インスタンスを作るときに決まる値だけ。デプロイの手順（deploy.sh）と本番の構成
# （docker-compose.prod.yml）は、デプロイのたびに CI が検査した SHA のものを SSM Run Command が
# 配る（.github/workflows/deploy.yml）。cloud-init はインスタンスごとに初回しか実行しないため、
# ここで手順まで配ると、ファイルを git で変えても稼働中のホストは古いままになる。
cat > /opt/abservice/deploy.env <<'ENV_EOF'
REGION=${aws_region}
PROJECT=${project_name}
ENVIRONMENT=${environment}
ENV_EOF
