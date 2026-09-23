variable "region" { type = string }
variable "account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "Use the explicit target account ID."
  }
}
variable "name" {
  type = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,30}$", var.name))
    error_message = "Use a simple, short host name."
  }
}
variable "availability_zone" { type = string }
variable "operator_cidr" {
  type = string
  validation {
    condition     = can(cidrhost(var.operator_cidr, 0)) && can(regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}/32$", var.operator_cidr))
    error_message = "Bootstrap SSH must be restricted to one operator IPv4 address."
  }
}
variable "assets_bucket" { type = string }
variable "parameter_prefix" {
  type = string
  validation {
    condition     = can(regex("^/[a-zA-Z0-9_/-]+[^/]$", var.parameter_prefix))
    error_message = "Use an absolute Parameter Store prefix without trailing slash."
  }
}
variable "backup_writer_policy_arn" { type = string }
variable "ca_certificate" {
  type = string
  validation {
    condition     = strcontains(var.ca_certificate, "BEGIN CERTIFICATE") && !strcontains(var.ca_certificate, "PRIVATE KEY")
    error_message = "Supply only the public CA certificate, never a private key."
  }
}
