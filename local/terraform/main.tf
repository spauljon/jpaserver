# FHIR stream: persists Observation upserts
resource "jetstream_stream" "fhir" {
  name             = var.stream_name
  subjects         = [var.fhir_subject] # can be ["fhir.obs.*"] if you plan more
  retention        = "workqueue"        # remove once any consumer acks
  storage          = "file"
  replicas         = 1 # set 3 in production clusters
  discard          = "old"
  max_age          = 60 * 60 * 24 * 7 # 7 days (seconds)
  duplicate_window = 60 * 2           # 2 minutes (seconds)
}

# DLQ stream for poison messages
resource "jetstream_stream" "dlq" {
  name      = "FHIR_DLQ"
  subjects  = ["fhir.obs.dlq"]
  retention = "limits" # keep until limits/age hit
  storage   = "file"
  replicas  = 1
  max_age   = 60 * 60 * 24 * 14 # 14 days
}

# Durable pull consumer for the vector worker
resource "jetstream_consumer" "vecdb" {
  stream_id    = jetstream_stream.fhir.id
  durable_name = var.durable_name

  # Pull consumer (no deliver_subject); your worker uses pullSubscribe.
  ack_policy     = "explicit"
  deliver_all    = true
  filter_subject = var.fhir_subject

  # Flow control/backpressure knobs
  max_ack_pending = 10000
  ack_wait        = 30 # seconds
  replay_policy   = "instant"
}
