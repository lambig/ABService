output "cloudfront_domain_name" {
  description = "CloudFrontディストリビューションのドメイン名（Route53エイリアスのターゲット）"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "ecr_repository_url" {
  description = "backendコンテナイメージのpush/pull先ECRリポジトリURL"
  value       = aws_ecr_repository.backend.repository_url
}

output "ec2_instance_id" {
  description = "backend EC2インスタンスID（SSM Session Manager接続に使用）"
  value       = aws_instance.backend.id
}

output "rds_endpoint" {
  description = "RDSエンドポイント（ホスト:ポート）"
  value       = aws_db_instance.main.endpoint
}

output "assets_bucket_name" {
  description = "アセットアップロード用S3バケット名"
  value       = aws_s3_bucket.assets.bucket
}

output "frontend_public_bucket_name" {
  description = "frontend-public配信用S3バケット名"
  value       = aws_s3_bucket.frontend_public.bucket
}

output "frontend_admin_bucket_name" {
  description = "frontend-admin配信用S3バケット名"
  value       = aws_s3_bucket.frontend_admin.bucket
}
