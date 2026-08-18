#!/bin/bash
# CI（GitHub Actions）からSSM Run Command経由で呼び出される。
# 引数1: デプロイ対象のフルイメージ参照（例: <account>.dkr.ecr.<region>.amazonaws.com/abservice-backend:<tag>）
set -euo pipefail

IMAGE="$1"
REGION="${aws_region}"
PROJECT="${project_name}"
ENVIRONMENT="${environment}"

REGISTRY="$(echo "$IMAGE" | cut -d/ -f1)"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

DB_HOST=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/host" --query 'Parameter.Value' --output text --region "$REGION")
DB_PORT=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/port" --query 'Parameter.Value' --output text --region "$REGION")
DB_NAME_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/name" --query 'Parameter.Value' --output text --region "$REGION")
DB_USERNAME_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/username" --query 'Parameter.Value' --output text --region "$REGION")
DB_PASSWORD_VALUE=$(aws ssm get-parameter --name "/$PROJECT/$ENVIRONMENT/db/password" --with-decryption --query 'Parameter.Value' --output text --region "$REGION")

export BACKEND_IMAGE="$IMAGE"
export DB_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME_VALUE"
export DB_REACTIVE_URL="postgresql://$DB_HOST:$DB_PORT/$DB_NAME_VALUE"
export DB_USERNAME="$DB_USERNAME_VALUE"
export DB_PASSWORD="$DB_PASSWORD_VALUE"

cd /opt/abservice
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker image prune -f
