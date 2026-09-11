#!/bin/bash
# 本番のホスト上でイメージを差し替える。CI（GitHub Actions）から SSM Run Command 経由で呼ばれ、
# 呼び出しの直前に、CI が検査した SHA のこのファイルと docker-compose.prod.yml が
# /opt/abservice へ置かれる（.github/workflows/deploy.yml）。
#
# 引数1: デプロイ対象のフルイメージ参照（例: <account>.dkr.ecr.<region>.amazonaws.com/abservice-backend:<tag>）
#
# インスタンスに固有の値はここへ書かず、user_data が置く /opt/abservice/deploy.env から読む。
# 前者はインスタンスを作るときに決まり、この手順はアプリと同じ速さで変わる。
set -euo pipefail

IMAGE="$1"

readonly ENV_FILE=/opt/abservice/deploy.env

if [ ! -f "$ENV_FILE" ]; then
  echo "$ENV_FILE is missing. The instance bootstrap (user_data) has not run on this host." >&2
  exit 1
fi

# shellcheck source=/dev/null
. "$ENV_FILE"

: "${REGION:?$ENV_FILE must define REGION}"
: "${PROJECT:?$ENV_FILE must define PROJECT}"
: "${ENVIRONMENT:?$ENV_FILE must define ENVIRONMENT}"

REGISTRY="$(echo "$IMAGE" | cut -d/ -f1)"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

DB_HOST_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/host" --query 'Parameter.Value' --output text --region "$REGION")
DB_PORT_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/port" --query 'Parameter.Value' --output text --region "$REGION")
DB_NAME_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/name" --query 'Parameter.Value' --output text --region "$REGION")
DB_USERNAME_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/username" --query 'Parameter.Value' --output text --region "$REGION")
DB_PASSWORD_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/password" --with-decryption --query 'Parameter.Value' --output text --region "$REGION")
ADMIN_API_KEY_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/app/admin-api-key" --with-decryption --query 'Parameter.Value' --output text --region "$REGION")
ASSETS_BUCKET_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/assets/bucket" --query 'Parameter.Value' --output text --region "$REGION")

export BACKEND_IMAGE="$IMAGE"
export DB_HOST="$DB_HOST_VALUE"
export DB_PORT="$DB_PORT_VALUE"
export DB_NAME="$DB_NAME_VALUE"
export DB_USERNAME="$DB_USERNAME_VALUE"
export DB_PASSWORD="$DB_PASSWORD_VALUE"
export ADMIN_API_KEY="$ADMIN_API_KEY_VALUE"
export ASSETS_BUCKET="$ASSETS_BUCKET_VALUE"

cd /opt/abservice
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker image prune -f
