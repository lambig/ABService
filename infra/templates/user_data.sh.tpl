#!/bin/bash
set -eu

dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user

# docker compose（CLI プラグイン）は AL2023 のリポジトリに無い。公式の手順どおり、プラグインの
# 置き場へバイナリを入れる。版は固定し、配布されている sha256 と突き合わせる（上流の最新へ
# 黙って追随すると、インスタンスを作った時期だけで実機の compose が変わる）。
COMPOSE_VERSION=v5.5.1
COMPOSE_ASSET="docker-compose-linux-$(uname -m)"
COMPOSE_RELEASE="https://github.com/docker/compose/releases/download/$COMPOSE_VERSION"
PLUGIN_DIR=/usr/libexec/docker/cli-plugins

install -d "$PLUGIN_DIR"
curl -fsSL "$COMPOSE_RELEASE/$COMPOSE_ASSET" -o "$PLUGIN_DIR/docker-compose"
curl -fsSL "$COMPOSE_RELEASE/$COMPOSE_ASSET.sha256" -o /tmp/docker-compose.sha256
# 配布物は「<hash>  <資産名>」の形。置いた先の名前へ読み替えて突き合わせる
echo "$(cut -d' ' -f1 /tmp/docker-compose.sha256)  $PLUGIN_DIR/docker-compose" | sha256sum -c -
chmod +x "$PLUGIN_DIR/docker-compose"
rm -f /tmp/docker-compose.sha256

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
