#!/usr/bin/env bash

set -x

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8010 \
  -jar ./target/fhir.jar
