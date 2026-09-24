mock_provider "aws" {}

override_data {
  target = data.aws_ip_ranges.origin
  values = { cidr_blocks = ["192.0.2.0/24", "198.51.100.0/24"] }
}

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
run "https_only_from_cloudfront" {
  command = plan
  variables { origin_https_enabled = true }
  assert {
    condition = length(aws_lightsail_instance_public_ports.host.port_info) == 2 && alltrue([
      for p in aws_lightsail_instance_public_ports.host.port_info :
      p.protocol == "tcp" && p.from_port == p.to_port && (
        (p.from_port == 22 && toset(p.cidrs) == toset(["192.0.2.1/32"])) ||
        (p.from_port == 443 && toset(p.cidrs) == toset(["192.0.2.0/24", "198.51.100.0/24"]))
      )
    ])
    error_message = "HTTPS must allow only origin-facing ranges; application and database ports stay closed."
  }
}
run "http_challenge_is_separate_opt_in" {
  command = plan
  variables { origin_http_validation_enabled = true }
  assert {
    condition = length(aws_lightsail_instance_public_ports.host.port_info) == 2 && alltrue([
      for p in aws_lightsail_instance_public_ports.host.port_info :
      p.protocol == "tcp" && p.from_port == p.to_port && (
        (p.from_port == 22 && toset(p.cidrs) == toset(["192.0.2.1/32"])) ||
        (p.from_port == 80 && toset(p.cidrs) == toset(["0.0.0.0/0"]))
      )
    ])
    error_message = "The ACME phase must not also open HTTPS or application ports."
  }
}
run "reject_empty_origin_ranges" {
  command = plan
  variables { origin_https_enabled = true }
  override_data {
    target = data.aws_ip_ranges.origin
    values = { cidr_blocks = [] }
  }
  expect_failures = [aws_lightsail_instance_public_ports.host]
}
run "reject_origin_rule_overflow" {
  command = plan
  variables {
    origin_https_enabled           = true
    origin_http_validation_enabled = true
  }
  override_data {
    target = data.aws_ip_ranges.origin
    values = { cidr_blocks = ["10.0.0.0/24", "10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24", "10.0.4.0/24", "10.0.5.0/24", "10.0.6.0/24", "10.0.7.0/24", "10.0.8.0/24", "10.0.9.0/24", "10.0.10.0/24", "10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24", "10.0.14.0/24", "10.0.15.0/24", "10.0.16.0/24", "10.0.17.0/24", "10.0.18.0/24", "10.0.19.0/24", "10.0.20.0/24", "10.0.21.0/24", "10.0.22.0/24", "10.0.23.0/24", "10.0.24.0/24", "10.0.25.0/24", "10.0.26.0/24", "10.0.27.0/24", "10.0.28.0/24", "10.0.29.0/24", "10.0.30.0/24", "10.0.31.0/24", "10.0.32.0/24", "10.0.33.0/24", "10.0.34.0/24", "10.0.35.0/24", "10.0.36.0/24", "10.0.37.0/24", "10.0.38.0/24", "10.0.39.0/24", "10.0.40.0/24", "10.0.41.0/24", "10.0.42.0/24", "10.0.43.0/24", "10.0.44.0/24", "10.0.45.0/24", "10.0.46.0/24", "10.0.47.0/24", "10.0.48.0/24", "10.0.49.0/24", "10.0.50.0/24", "10.0.51.0/24", "10.0.52.0/24", "10.0.53.0/24", "10.0.54.0/24", "10.0.55.0/24", "10.0.56.0/24", "10.0.57.0/24", "10.0.58.0/24", "10.0.59.0/24"] }
  }
  expect_failures = [aws_lightsail_instance_public_ports.host]
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
