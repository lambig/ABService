# Independent cohost provisioning (opt-in)

This Terraform root provisions a Lightsail 2GB Amazon Linux host, attached static
IPv4, a private versioned asset bucket, immutable-tag ECR repository, separate
app/deploy/backup Roles Anywhere roles and profiles, an SSM hybrid-node role, and
operator-only Secrets Manager **metadata**. It does not provision DNS, CDN, TLS,
the legacy EC2/RDS stack, activation codes, Parameter Store values, or host software.
Use `infra/host/cohost` for the application/database runtime and `infra/cohost-backup`
for independent backup storage/monitoring. Initial backup and notification
acceptance remain required before accumulating real updates.

## Inputs and state

Supply the explicit account, region/zone, resource name, asset bucket, parameter
prefix, one operator IPv4 `/32`, backup writer policy ARN, and **public** CA
certificate through private operational configuration. Use an independent remote
state key. Do not apply both this root and the legacy root to the same bucket or
repository. A later CDN configuration must reference existing resources or perform
an explicit state ownership migration; do not duplicate ownership.

```sh
terraform init -backend-config=/private/backend.hcl
terraform plan -var-file=/private/host.tfvars.json -out=/private/host.plan
# Inspect the saved plan, account, resource names, and costs before applying it.
terraform apply /private/host.plan
```

Only SSH from the specified operator is opened. The application must initially
bind to loopback and PostgreSQL must remain on the internal Docker network. The
CDN/origin firewall, origin authentication, TLS, headers and acceptance are separate
work. Creating the host is not a public release.

### Optional asset delivery and browser uploads

This root continues to own the existing asset bucket and its policy/CORS. Set
`assets_distribution_arn` to one exact same-account CloudFront distribution ARN
after configuring OAC signing in the edge root. Only `s3:GetObject` on `assets/*`
is granted to that distribution; pending uploads, object versions, listing and
writes are not granted to CloudFront. The TLS-only deny and public access blocks
remain. A null ARN, the default, leaves the original bootstrap policy unchanged.

Set `asset_upload_origins` to the exact HTTPS browser origins that host the admin
application, including a temporary delivery-test origin when needed. CORS permits
only PUT with Content-Type and exposes ETag; it does not grant S3 authentication.
The backend must still authorize and sign each upload. An empty set, the default,
does not manage a CORS configuration; changing an existing nonempty set to empty
removes the CORS resource. Remove temporary origins after acceptance.

Check the saved plan for only the intended bucket policy/CORS changes. Read back
the actual bucket policy and CORS, test allowed and disallowed preflights, then
verify signed uploads and OAC retrieval through the real distribution. CORS or
policy readback alone is not browser/end-to-end acceptance. This option neither
opens host ports nor configures origin TLS or DNS.

The host, asset bucket, repository and CA secret have `prevent_destroy`; this
protects against ordinary destructive plans, not manual deletion or removal of the
resource declaration. Published `assets/` versions have no expiration rule. Only
`pending/` is expired. The app role may delete pending uploads but cannot delete
published objects or any object version. ECR tags are immutable; this root does
not expire tagged images. Track storage growth and preserve the deployed/previous
digests when performing explicit image retention.

## Secret and certificate handoff

Generate the CA outside Terraform. Only its certificate enters the input/state.
Write the CA private key plus issuance/revocation ledger into the created secret
using the operator credential, verify retrieval and key/certificate consistency,
and remove the local signing material. None of the workload policies permits
Secrets Manager access. The deployment role only reads the configured Parameter
Store prefix and pulls from the dedicated repository. The backup role only gets
the separately reviewed writer policy; never add broad permissions to it.

The SSM management role retains `AmazonSSMManagedInstanceCore` for node management,
but explicitly denies all Parameter Store reads, including history and recursive
path reads. This overrides the managed policy's broad parameter grants, including
parameters encrypted with the default SSM key. SSM commands must use the separate
deployment credential when application parameters are needed; do not rely on the
agent credential to resolve Parameter Store references in SSM documents. Verify
both denied parameter reads and continued node/Run Command operation after apply.

Issue separate leaf certificates with CN `<name>-app`, `<name>-deploy` and
`<name>-backup`. The trust policy checks subject, trust anchor and source account.
Use 900-second sessions. Store the leaf keys under separate protected directories;
do not send the CA key to the host. Activation codes and secret values must not
enter user-data, SSM command text, Terraform or logs. Bootstrap through an encrypted
channel with a pinned host key, then register one SSM node. Confirm process
credentials by exercising the actual allowed API and checking denied operations.

Operators must keep certificate/CA/CRL expiry and renewal records, and verify the
retrieval route independently of the host. Register/revise the CRL through Roles
Anywhere: hosting a CRL URL alone is insufficient. Rotation and emergency session
revocation use the existing operational procedures.

## Checks

`terraform validate` and `terraform test` check the schema, bootstrap port and
asset-retention and SSM parameter-read boundaries, and reject a world-open SSH
range or private-key input.
Tests use a mock provider and never create AWS resources. Mock plan checks are not
an AWS IAM/access or real-host acceptance test; read back actual trust policies,
role permissions, bucket settings and host behavior after applying.
