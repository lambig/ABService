#!/bin/bash
set -eu

dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

# docker compose（CLI プラグイン）は AL2023 のリポジトリに無い。公式の手順どおり、プラグインの
# 置き場へバイナリを入れる。
#
# ダイジェストはここに持つ。版の名前だけでは実体が決まらず（GitHub の release は差し替えられる）、
# 配布物と一緒に置かれた .sha256 を使うと、両方を差し替えられたときに突き合わせが素通りする。
# 版を上げるときはこの値も一緒に変える。
#
# 資産は arm64 のものに固定する。本番の EC2 は arm64（compute.tf が arm64 の AMI を選ぶ）で、
# 架構を変えるなら資産名とダイジェストの両方を変えることになる。
COMPOSE_VERSION=v5.5.1
COMPOSE_ASSET=docker-compose-linux-aarch64
COMPOSE_SHA256=732e3a84c1a0f67256ce80bc2598a24546b10ca05f9faa97efceb1171ece2ef7
PLUGIN_DIR=/usr/libexec/docker/cli-plugins

install -d "$PLUGIN_DIR"
curl -fsSL "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/$COMPOSE_ASSET" \
  -o "$PLUGIN_DIR/docker-compose"
echo "$COMPOSE_SHA256  $PLUGIN_DIR/docker-compose" | sha256sum -c -
chmod +x "$PLUGIN_DIR/docker-compose"

# bootstrap が済んだことの検査。deploy.sh は docker compose を呼ぶため、ここで揃っていなければ
# 最初のデプロイが失敗する。set -e により、失敗は cloud-init のログへ残る
docker compose version

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
