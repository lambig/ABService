# CloudFrontからの直接オリジンアクセスのみを許可し、EC2への直接到達（WAFバイパス）を防ぐ。
data "aws_ec2_managed_prefix_list" "cloudfront_origin_facing" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "CloudFrontオリジンからのbackendポート宛のみ許可（SSHは開放しない。管理はSSM Session Manager経由）"
  vpc_id      = aws_vpc.main.id

  # ビューア向けTLS終端はCloudFront側で行い、CloudFront〜EC2間はAWSバックボーン内のHTTPとする
  # （EC2側に証明書を持たせる運用コストを避けるため）。
  ingress {
    description     = "backend app port from CloudFront"
    from_port       = var.backend_app_port
    to_port         = var.backend_app_port
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront_origin_facing.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-ec2-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "backend EC2からのPostgreSQL接続のみ許可"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from backend EC2"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds-sg"
  }
}
