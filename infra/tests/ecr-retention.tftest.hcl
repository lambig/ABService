mock_provider "aws" {}
mock_provider "aws" {
  alias = "us_east_1"
}
mock_provider "archive" {}
mock_provider "random" {}
mock_provider "tls" {}

variables {
  domain_name = "example.invalid"
}

// The deploy workflow issues exactly one tag per image (sha-<full SHA>). The retention rule
// must select that prefix alone; any other prefix keeps images out of the count forever.
run "retain_only_deployed_tags" {
  command = plan
  plan_options {
    target = [aws_ecr_repository.backend, aws_ecr_lifecycle_policy.backend]
  }

  assert {
    condition     = aws_ecr_repository.backend.image_tag_mutability == "IMMUTABLE"
    error_message = "A commit tag must never move to another digest; rollback restores the artifact first deployed for that commit."
  }

  assert {
    condition = alltrue([
      for rule in jsondecode(aws_ecr_lifecycle_policy.backend.policy).rules :
      rule.selection.tagPrefixList == ["sha-"] && rule.selection.countType == "imageCountMoreThan" && rule.selection.countNumber == 10
      if rule.selection.tagStatus == "tagged"
    ]) && length([for rule in jsondecode(aws_ecr_lifecycle_policy.backend.policy).rules : rule if rule.selection.tagStatus == "tagged"]) == 1
    error_message = "Tagged images must be retained by the single deploy prefix (sha-), keeping the 10 most recent."
  }

  assert {
    condition = length([
      for rule in jsondecode(aws_ecr_lifecycle_policy.backend.policy).rules :
      rule if rule.selection.tagStatus == "untagged" && rule.selection.countType == "sinceImagePushed"
    ]) == 1
    error_message = "Untagged layers must still expire by age."
  }
}
