#!/usr/bin/env bash

set -x

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8010 \
  -jar ./target/fhir.jar
