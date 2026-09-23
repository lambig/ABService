variable "aws_region" {
  type = string
}

variable "account_id" {
  type = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "Set the intended AWS account ID."
  }
}

variable "name" {
  type    = string
  default = "cohost-backup"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,39}$", var.name))
    error_message = "Use a lowercase resource name of at most 40 characters."
  }
}

variable "bucket_name" {
  type = string
}

variable "assets_bucket_name" {
  type        = string
  description = "Existing or planned asset bucket; this stack only creates the writer policy for it."
  validation {
    condition     = var.assets_bucket_name != var.bucket_name
    error_message = "Assets and database backups must use separate buckets."
  }
}

variable "prefix" {
  type    = string
  default = "cohost"
  validation {
    condition     = can(regex("^[a-zA-Z0-9][a-zA-Z0-9/_-]*[a-zA-Z0-9]$", var.prefix))
    error_message = "Use a prefix with no wildcard or leading/trailing slash."
  }
}

variable "alarm_email" {
  type = string
  validation {
    condition     = can(regex("^[^@ ]+@[^@ ]+\\.[^@ ]+$", var.alarm_email))
    error_message = "Set the notification email; confirm the SNS subscription after applying."
  }
}

variable "monitoring_enabled" {
  type        = bool
  default     = false
  description = "Enable after the first real backup, subscriber confirmation and a successful manual Lambda invocation."
}

variable "max_age_hours" {
  type    = number
  default = 18
  validation {
    condition     = var.max_age_hours > 0 && var.max_age_hours < 24
    error_message = "Warn before the one-day data-loss limit."
  }
}
