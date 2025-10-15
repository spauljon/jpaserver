output "fhir_stream"   { value = jetstream_stream.fhir.name }
output "dlq_stream"    { value = jetstream_stream.dlq.name }
output "durable_name"  { value = jetstream_consumer.vecdb.durable_name }
