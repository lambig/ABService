#!/bin/bash
# 本番のホスト上でイメージを差し替える。CI（GitHub Actions）から SSM Run Command 経由で呼ばれ、
# 呼び出しの直前に、CI が検査した SHA のこのファイルと docker-compose.prod.yml が
# /opt/abservice へ置かれる（.github/workflows/deploy.yml）。
#
# 引数1: デプロイ対象のフルイメージ参照。digest で指す
#        （例: <account>.dkr.ecr.<region>.amazonaws.com/abservice-backend@sha256:<digest>）
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
ORIGIN_VERIFY_TOKEN_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/app/origin-verify-token" --with-decryption --query 'Parameter.Value' --output text --region "$REGION")
BACKEND_LOG_GROUP_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/monitoring/log-group" --query 'Parameter.Value' --output text --region "$REGION")

# prod の compose に、ログを CloudWatch へ運ぶ上書きを重ねる（#168）。両方とも deploy.yml が配る
COMPOSE=(docker compose -f docker-compose.prod.yml -f docker-compose.logs.yml)

# awslogs ドライバの宛先。REGION は deploy.env のもの（配線の検査が `export 名前=` の形で読むため代入で書く）
export REGION="$REGION"
export BACKEND_LOG_GROUP="$BACKEND_LOG_GROUP_VALUE"
export BACKEND_IMAGE="$IMAGE"
export DB_HOST="$DB_HOST_VALUE"
export DB_PORT="$DB_PORT_VALUE"
export DB_NAME="$DB_NAME_VALUE"
export DB_USERNAME="$DB_USERNAME_VALUE"
export DB_PASSWORD="$DB_PASSWORD_VALUE"
export ADMIN_API_KEY="$ADMIN_API_KEY_VALUE"
export ASSETS_BUCKET="$ASSETS_BUCKET_VALUE"
export ORIGIN_VERIFY_TOKEN="$ORIGIN_VERIFY_TOKEN_VALUE"

cd /opt/abservice
"${COMPOSE[@]}" pull

# 起動を始めたことを成功にしない。compose の healthcheck（readiness を引く。DB 接続を含む）が
# 通るまで待ち、期限を切る。待たずに終えると、起動に失敗しても unhealthy のままでも SSM の実行は
# 成功で終わり、Actions も緑になる。期限は healthcheck の start_period と retries を見込む。
if ! "${COMPOSE[@]}" up -d --wait --wait-timeout 240; then
  echo "The backend did not become healthy in time. Its log follows." >&2
  "${COMPOSE[@]}" logs --no-color --tail 200 backend >&2
  exit 1
fi

# 何が動いているかを、渡した参照ではなく稼働中のコンテナから読んで残す。証跡（運用側の台帳）が
# 記録する「稼働 image digest」はこの行から取る。コンテナの inspect は image ID しか持たないため、
# その ID のイメージから RepoDigests を読む（手元でビルドしただけのイメージでは空になるので落とさない）。
# CI はこの行をそのまま取り出して実コンテナに対して走らせる（ci.yml の container-check）。1行に保つ
echo "running image: $(docker image inspect "$(docker inspect abservice-backend --format '{{.Image}}')" --format '{{.Id}}{{range .RepoDigests}} {{.}}{{end}}')"

# 差し替えで参照されなくなったレイヤを片付けるのは、新しいものが healthy になってから。失敗した
# ときに手元へ残しておけば、戻すときに pull を待たずに済む。
# ここで消えるのは dangling なものだけで、commit SHA のタグが付いた旧世代は残る（#336）。
docker image prune -f
