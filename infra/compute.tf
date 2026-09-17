data "aws_ami" "al2023_arm64" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-arm64"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# SSHは開放せずSSM Session Manager経由で運用・パッチ適用する。
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "app" {
  name = "${var.project_name}-ec2-app-policy"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadAppSecrets"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParametersByPath"]
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/*"
      },
      {
        Sid      = "DecryptAppSecrets"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "arn:aws:kms:${var.aws_region}:${data.aws_caller_identity.current.account_id}:alias/aws/ssm"
      },
      {
        Sid      = "AssetBucketAccess"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "${aws_s3_bucket.assets.arn}/*"
      },
      # 確定した画像（assets/）は追記のみで、消す経路を持たない。過去の時点へ戻した DB が参照する画像が
      # 必ず在るのはこの性質による（#130）。消せるのは受け入れ前（pending/）だけ
      {
        Sid      = "AssetPendingDelete"
        Effect   = "Allow"
        Action   = "s3:DeleteObject"
        Resource = "${aws_s3_bucket.assets.arn}/pending/*"
      },
      {
        Sid      = "AssetBucketList"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.assets.arn
      },
      {
        Sid      = "EcrAuth"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "EcrPullBackendImage"
        Effect = "Allow"
        Action = [
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = aws_ecr_repository.backend.arn
      },
      {
        # Docker の awslogs ドライバが backend のログを運ぶ（docker-compose.logs.yml）。宛先はこのグループだけ
        Sid      = "WriteBackendLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.backend.arn}:*"
      },
      {
        # CloudWatch agent がホストのディスク・メモリを送る。名前空間は agent の設定と揃える
        Sid       = "PutHostMetrics"
        Effect    = "Allow"
        Action    = "cloudwatch:PutMetricData"
        Resource  = "*"
        Condition = { StringEquals = { "cloudwatch:namespace" = local.host_metrics_namespace } }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-instance-profile"
  role = aws_iam_role.ec2.name
}

locals {
  user_data = templatefile("${path.module}/templates/user_data.sh.tpl", {
    aws_region                 = var.aws_region
    project_name               = var.project_name
    environment                = var.environment
    cloudwatch_agent_parameter = aws_ssm_parameter.cloudwatch_agent_config.name
  })
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.al2023_arm64.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  root_block_device {
    volume_size = var.ec2_root_volume_size_gb
    volume_type = "gp3"
    encrypted   = true
  }

  # Dockerランタイムの準備と、インスタンスに固有の値（/opt/abservice/deploy.env）まで。
  # deploy.sh と docker-compose.prod.yml はデプロイのたびに SSM Run Command が配る（#284）。
  user_data = local.user_data

  tags = {
    Name = "${var.project_name}-backend"
  }
}
