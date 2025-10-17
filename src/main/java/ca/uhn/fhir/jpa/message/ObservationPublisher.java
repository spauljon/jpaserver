package ca.uhn.fhir.jpa.message;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.messaging.BaseResourceMessage.OperationTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public class ObservationPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationPublisher.class);

    private static final String OPERATION = "op";
    private static final String OBSERVATION = "observation";

    private final JetStream js;
    private final String subject;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IParser parser;

    public ObservationPublisher(@Nonnull final JetStream js,
                                @Nonnull final String subject) {
        this.js = js;
        this.subject = subject;
        this.parser = FhirContext.forR4().newJsonParser();
    }

    public void publish(@Nonnull final Observation obs,
                        @Nonnull final OperationTypeEnum operation) {
        ofNullable(toEvent(obs, operation))
                .map(this::encodeEvent)
                .ifPresent(this::publishEvent);
    }

    @Nullable
    private byte[] encodeEvent(@Nonnull final Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event).getBytes(UTF_8);
        } catch (final JsonProcessingException e) {
            LOGGER.error("json serialization error", e);
        }

        return null;
    }

    private void publishEvent(@Nonnull final byte[] b) {
        try {
            js.publish(subject, b);
        } catch (final JetStreamApiException | IOException e) {
            LOGGER.error("jetstream publish error", e);
        }
    }

    @Nullable
    private Map<String, Object> toEvent(@Nonnull final Observation o,
                                        @Nonnull final OperationTypeEnum operation) {
        try {
            return Map.of(OPERATION, operation.name().toLowerCase(),
                          OBSERVATION, parser.encodeResourceToString(o));
        } catch (final DataFormatException e) {
            LOGGER.error("json serialization error; forgoing NATS publish operation", e);
        }

        return null;
    }
}
