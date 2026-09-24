variable "region" { type = string }
variable "account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "Specify the intended account."
  }
}
variable "name" { type = string }
variable "assets_bucket" { type = string }
variable "backend_domain" {
  type = string
  validation {
    condition     = can(regex("^[a-zA-Z0-9][a-zA-Z0-9.-]+[a-zA-Z]$", var.backend_domain))
    error_message = "Use a resolvable origin DNS name, without a scheme or path."
  }
}
variable "backend_port" {
  type    = number
  default = 8080
}
variable "backend_https_port" {
  type    = number
  default = 443
}
variable "backend_protocol" {
  type    = string
  default = "https-only"
  validation {
    condition     = contains(["https-only", "http-only"], var.backend_protocol)
    error_message = "Explicitly select HTTP or HTTPS to the restricted origin."
  }
}
variable "origin_verify_token" {
  type      = string
  sensitive = true
  validation {
    condition     = length(var.origin_verify_token) >= 32
    error_message = "Supply the existing high-entropy origin verifier securely."
  }
}
variable "enabled" {
  type    = bool
  default = false
}
variable "free_plan_verified" {
  type    = bool
  default = false
}
variable "public_indexing_enabled" {
  type    = bool
  default = false
}
variable "aliases" {
  type    = list(string)
  default = []
}
variable "viewer_certificate_arn" {
  type    = string
  default = null
  validation {
    condition     = var.viewer_certificate_arn == null ? true : can(regex("^arn:aws:acm:us-east-1:", var.viewer_certificate_arn))
    error_message = "CloudFront requires an ACM certificate in us-east-1."
  }
}
