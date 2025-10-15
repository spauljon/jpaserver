terraform {
  required_version = ">= 1.5.0"
  required_providers {
    jetstream = {
      source  = "nats-io/jetstream"
      version = "~> 0.2"
    }
  }
}

provider "jetstream" {
  # Comma-separated list for HA clusters, e.g. "nats://n1:4222,nats://n2:4222,nats://n3:4222"
  servers         = var.nats_servers
  # Choose one auth method you use in your cluster:
  # credentials     = var.nats_credentials_file  # path to .creds file (optional)
  # credential_data = var.nats_credentials_data # raw creds as string (optional)
  # user           = var.nats_user              # (optional)
  # password       = var.nats_password          # (optional)
  # nkey           = var.nats_nkey_file         # (optional)
}
