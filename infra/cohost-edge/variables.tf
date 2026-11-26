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
variable "backend_response_timeout" {
  description = "API origin response wait; raise explicitly for private audio after checking the distribution quota and real upload timings."
  type        = number
  default     = 30
  nullable    = false
  validation {
    condition     = var.backend_response_timeout >= 1 && var.backend_response_timeout <= 60 && floor(var.backend_response_timeout) == var.backend_response_timeout
    error_message = "Use an integer from 1 to 60 seconds supported by the pinned provider."
  }
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
variable "listening_enabled" {
  description = "Explicitly allow the offline listening shell. API/token authorization remains independent."
  type        = bool
  default     = false
  nullable    = false
}
variable "private_audio_upload_enabled" {
  description = "Opt in to the WAF body-size exception for the exact private FLAC PUT endpoint. Origin authorization, feature flag and 256MiB limit remain required."
  type        = bool
  default     = false
  nullable    = false
}
variable "listening_audio_origins" {
  description = "Exact private audio HTTPS origins for browser downloads; no signed URLs or credentials."
  type        = set(string)
  default     = []
  nullable    = false
  validation {
    condition     = length(var.listening_audio_origins) <= 4 && alltrue([for origin in var.listening_audio_origins : can(regex("^https://[a-z0-9][a-z0-9.-]*[a-z0-9](:[0-9]{1,5})?$", origin))])
    error_message = "Use at most four exact HTTPS origins, without wildcard, path, query or credentials."
  }
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
