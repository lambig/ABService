# backendコンテナイメージの配布先（#121）。CI/CD（#128）からpushし、EC2からpullする。
#
# タグは commit ごとに1つ（sha-<full SHA>）で、一度押し込んだら動かさない。同じ commit を作り直しても同じ実体には
# ならない（ベースイメージやパッケージの取得を含む）ため、タグが別の digest へ移ると、以前その commit で配った実体が
# untagged になって消え、ロールバックが「当時の実体」を戻せなくなる。配布は在るタグを再利用し（deploy.yml）、
# IMMUTABLE はそれ以外の経路からもタグを動かせないようにする。保持規則で期限切れになったタグは消えるため、その後に
# 同じ commit を配り直す経路は残る（そのときは新しい実体になる）。
resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}-backend"
  image_tag_mutability = "IMMUTABLE"

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
