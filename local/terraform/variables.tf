variable "nats_servers" {
  description = "NATS server(s) URL(s) for JetStream admin"
  type        = string
  default     = "nats://nats:4222"
}

variable "nats_credentials_file" {
  description = "Path to NATS .creds for JetStream admin (optional)"
  type        = string
  default     = ""
}

variable "fhir_subject" {
  description = "Subject for Observation vector writes"
  type        = string
  default     = "fhir.obs.vector"
}

variable "stream_name" {
  description = "JetStream stream name for FHIR events"
  type        = string
  default     = "FHIR"
}

variable "durable_name" {
  description = "Durable consumer name for the vector worker"
  type        = string
  default     = "vecdb"
}
