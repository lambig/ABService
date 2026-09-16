# backendコンテナイメージの配布先（#121）。CI/CD（#128）からpushし、EC2からpullする。
resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 無制限にイメージが溜まりストレージコストが増え続けるのを防ぐ。
#
# 配布（.github/workflows/deploy.yml）が発行するタグは `sha-<full SHA>` の1規則だけで、保持規則の接頭辞は
# それに揃える。ここに無い接頭辞のタグは規則の対象外になり、数に入らないまま残り続ける。両者が揃っていることは
# scripts/check-deploy-image-tag.sh が突き合わせる。手動ロールバックが戻せるのは、ここで保持している直近10件。
resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "配布が発行したタグ付きイメージは直近10件のみ保持する"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["sha-"]
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "未タグイメージは1日経過で削除する"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
