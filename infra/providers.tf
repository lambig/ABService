provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "ABService"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ACM証明書（CloudFront用）とWAF WebACL（scope=CLOUDFRONT）はAWSの制約によりus-east-1固定。
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "ABService"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
