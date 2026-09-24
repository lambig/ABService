mock_provider "aws" {}

override_resource {
  target = aws_s3_bucket.assets
  values = { arn = "arn:aws:s3:::example-assets", id = "example-assets" }
}
override_resource {
  target = aws_rolesanywhere_trust_anchor.host
  values = { arn = "arn:aws:rolesanywhere:us-east-1:123456789012:trust-anchor/example" }
}
override_resource {
  target = aws_ecr_repository.backend
  values = { arn = "arn:aws:ecr:us-east-1:123456789012:repository/example-backend" }
}
override_resource {
  target = aws_iam_role.workload["app"]
  values = { arn = "arn:aws:iam::123456789012:role/example-app" }
}
override_resource {
  target = aws_iam_role.workload["deploy"]
  values = { arn = "arn:aws:iam::123456789012:role/example-deploy" }
}
override_resource {
  target = aws_iam_role.workload["backup"]
  values = { arn = "arn:aws:iam::123456789012:role/example-backup" }
}
variables {
  region                   = "us-east-1"
  account_id               = "123456789012"
  name                     = "example"
  availability_zone        = "us-east-1a"
  operator_cidr            = "192.0.2.1/32"
  assets_bucket            = "example-assets"
  parameter_prefix         = "/example/host"
  backup_writer_policy_arn = "arn:aws:iam::123456789012:policy/example-writer"
  ca_certificate           = "-----BEGIN CERTIFICATE-----\ninvalid-public-fixture\n-----END CERTIFICATE-----"
}
run "bootstrap_ports_and_asset_retention" {
  command = plan
  assert {
    condition     = alltrue([for rule in aws_s3_bucket_lifecycle_configuration.assets.rule : rule.filter[0].prefix == "pending/"])
    error_message = "Never expire published asset versions."
  }
  assert {
    condition     = alltrue([for p in aws_lightsail_instance_public_ports.host.port_info : p.from_port == 22 && p.to_port == 22 && toset(p.cidrs) == toset(["192.0.2.1/32"])])
    error_message = "Do not expose application/database ports during bootstrap."
  }
}
run "reject_worldwide_ssh" {
  command = plan
  variables { operator_cidr = "0.0.0.0/0" }
  expect_failures = [var.operator_cidr]
}
run "ssm_cannot_read_workload_parameters" {
  command = plan
  assert {
    condition = (
      aws_iam_role_policy.ssm_parameter_boundary.role == "example-ssm" &&
      jsondecode(aws_iam_role_policy.ssm_parameter_boundary.policy).Statement[0].Effect == "Deny" &&
      jsondecode(aws_iam_role_policy.ssm_parameter_boundary.policy).Statement[0].Resource == "*" &&
      toset(jsondecode(aws_iam_role_policy.ssm_parameter_boundary.policy).Statement[0].Action) == toset([
        "ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath", "ssm:GetParameterHistory"
      ])
    )
    error_message = "The SSM agent must explicitly deny all parameter reads, including ancestor paths and history."
  }
  assert {
    condition = (
      aws_iam_role_policy_attachment.ssm.role == "example-ssm" &&
      aws_iam_role_policy_attachment.ssm.policy_arn == "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
    )
    error_message = "Keep SSM core management permissions on the bounded management role."
  }
}
run "reject_private_key_input" {
  command = plan
  variables { ca_certificate = "-----BEGIN CERTIFICATE-----\n-----BEGIN PRIVATE KEY-----" }
  expect_failures = [var.ca_certificate]
}
run "reject_ipv6_bootstrap_range" {
  command = plan
  variables { operator_cidr = "2001:db8::/32" }
  expect_failures = [var.operator_cidr]
}
run "asset_access_disabled_by_default" {
  command = plan
  assert {
    condition = (
      length(jsondecode(aws_s3_bucket_policy.assets.policy).Statement) == 1 &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[0].Effect == "Deny" &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[0].Condition.Bool["aws:SecureTransport"] == "false" &&
      length(aws_s3_bucket_cors_configuration.assets) == 0
    )
    error_message = "Bootstrap must retain the TLS deny and grant no CDN or browser-upload access."
  }
}
run "only_named_distribution_reads_published_assets" {
  command = plan
  variables {
    assets_distribution_arn = "arn:aws:cloudfront::123456789012:distribution/EEXAMPLE"
    asset_upload_origins    = ["https://admin.example.invalid"]
  }
  assert {
    condition = (
      length(jsondecode(aws_s3_bucket_policy.assets.policy).Statement) == 2 &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[1].Effect == "Allow" &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[1].Principal.Service == "cloudfront.amazonaws.com" &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[1].Action == "s3:GetObject" &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[1].Resource == "arn:aws:s3:::example-assets/assets/*" &&
      jsondecode(aws_s3_bucket_policy.assets.policy).Statement[1].Condition.StringEquals["AWS:SourceArn"] == "arn:aws:cloudfront::123456789012:distribution/EEXAMPLE"
    )
    error_message = "Grant only current published-object reads to the named distribution, never pending/version/list/write access."
  }
  assert {
    condition = alltrue([for rule in aws_s3_bucket_cors_configuration.assets[0].cors_rule :
      toset(rule.allowed_origins) == toset(["https://admin.example.invalid"]) &&
      toset(rule.allowed_methods) == toset(["PUT"]) &&
      toset(rule.allowed_headers) == toset(["Content-Type"]) &&
      toset(rule.expose_headers) == toset(["ETag"])
    ])
    error_message = "Browser CORS must allow only explicit origins and the presigned PUT contract."
  }
  assert {
    condition = (
      aws_s3_bucket_public_access_block.assets.block_public_acls &&
      aws_s3_bucket_public_access_block.assets.block_public_policy &&
      aws_s3_bucket_public_access_block.assets.ignore_public_acls &&
      aws_s3_bucket_public_access_block.assets.restrict_public_buckets &&
      alltrue([for p in aws_lightsail_instance_public_ports.host.port_info : p.from_port == 22 && p.to_port == 22])
    )
    error_message = "Asset access must not weaken bucket privacy or open host ports."
  }
}
run "reject_other_account_distribution" {
  command = plan
  variables { assets_distribution_arn = "arn:aws:cloudfront::000000000000:distribution/EEXAMPLE" }
  expect_failures = [var.assets_distribution_arn]
}
run "reject_wildcard_distribution" {
  command = plan
  variables { assets_distribution_arn = "arn:aws:cloudfront::123456789012:distribution/*" }
  expect_failures = [var.assets_distribution_arn]
}
run "reject_wildcard_upload_origin" {
  command = plan
  variables { asset_upload_origins = ["https://*.example.invalid"] }
  expect_failures = [var.asset_upload_origins]
}
run "reject_plaintext_upload_origin" {
  command = plan
  variables { asset_upload_origins = ["http://admin.example.invalid"] }
  expect_failures = [var.asset_upload_origins]
}
run "reject_upload_origin_path" {
  command = plan
  variables { asset_upload_origins = ["https://admin.example.invalid/admin"] }
  expect_failures = [var.asset_upload_origins]
}
