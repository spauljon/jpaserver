package ca.uhn.fhir.jpa.message;

import ca.uhn.fhir.rest.server.messaging.BaseResourceMessage.OperationTypeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static ca.uhn.fhir.util.DateUtils.formatDate;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public class ObservationPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationPublisher.class);

    private static final String UNKNOWN = "<unknown>";
    private static final String TYPE = "type";
    private static final String VERSION = "v";
    private static final String OPERATION = "op";
    private static final String OBSERVATION_ID = "observationId";
    private static final String PATIENT_ID = "patientId";
    private static final String EFFECTIVE_START = "effectiveStart";
    private static final String EFFECTIVE_END = "effectiveEnd";
    private static final String CODE_TEXT = "codeText";
    private static final String VALUE_TEXT = "valueText";

    private final JetStream js;
    private final String subject;
    private final ObjectMapper om = new ObjectMapper();

    public ObservationPublisher(@Nonnull final JetStream js,
                                @Nonnull final String subject) {
        this.js = js;
        this.subject = subject;
    }

    public void publish(@Nonnull final Observation obs,
                        @Nonnull final OperationTypeEnum operation) {
        ofNullable(toEvent(obs, operation))
                .map(event -> encodeEvent(obs, operation))
                .ifPresent(this::publishEvent);
    }

    @Nullable
    private byte[] encodeEvent(final @NotNull Observation obs,
                               final @NotNull OperationTypeEnum operation) {
        try {
            return om.writeValueAsString(toEvent(obs, operation)).getBytes(UTF_8);
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
        final var suffix = operation == OperationTypeEnum.DELETE ? toDeleteEvent(o) : null;

        return operation == OperationTypeEnum.CREATE || operation == OperationTypeEnum.UPDATE
               ? toUpsertEvent(o, operation) : suffix;
    }

    private Map<String, Object> toDeleteEvent(@Nonnull final Observation o) {
        return Map.of(TYPE, "ObservationOp",
                      VERSION, 1,
                      OPERATION, OperationTypeEnum.DELETE.name().toLowerCase(),
                      OBSERVATION_ID, extractId(o));
    }

    private Map<String, Object> toUpsertEvent(@Nonnull final Observation o,
                                              @Nonnull final OperationTypeEnum operation) {
        final var m =
                Map.of(TYPE, "ObservationOp",
                       VERSION, 1,
                       OPERATION, operation.name().toLowerCase(),
                       OBSERVATION_ID, extractId(o),
                       PATIENT_ID, extractPatientId(o),
                       EFFECTIVE_START, extractEffectiveStart(o),
                       EFFECTIVE_END, extractEffectiveEnd(o),
                       CODE_TEXT, extractCode(o),
                       VALUE_TEXT, extractValue(o));

        final var display = format(
                "Observation|subject=%s|code=%s|value=%s|effectiveStart='%s'|effectiveEnd='%s'",
                m.get(PATIENT_ID), m.get(CODE_TEXT), m.get(VALUE_TEXT), m.get(EFFECTIVE_START),
                m.get(EFFECTIVE_END));

        return ImmutableMap.<String, Object>builder()
                           .putAll(m)
                           .put("displayText", display)
                           .build();
    }

    @Nonnull
    private String extractValue(@Nonnull final Observation o) {
        final var suffix1 = (o.getValueQuantity().hasValue() ? o.getValueQuantity().getValue()
                                                                .toPlainString() : "")
                + (o.getValueQuantity().hasUnit() ? " " + o.getValueQuantity()
                                                           .getUnit() : "");
        final var suffix2 = o.hasValue() ? o.getValue().primitiveValue() : UNKNOWN;

        return o.hasValueQuantity() ? suffix1 : suffix2;
    }

    @Nonnull
    private Object extractCode(final Observation o) {
        final var suffix2 = o.getCode() != null ? o.getCode().getText() : UNKNOWN;
        final var suffix1 = (o.getCode().getCodingFirstRep().hasSystem()
                             ? o.getCode()
                                .getCodingFirstRep()
                                .getSystem() + " " : "")
                + o.getCode().getCodingFirstRep().getCode();
        return o.hasCode() && o.getCode().hasCoding() ? suffix1 : suffix2;
    }

    @Nonnull
    private String extractEffectiveStart(@Nonnull final Observation o) {
        return o.hasEffectivePeriod() && o.getEffectivePeriod().hasStart()
               ? formatDate(o.getEffectivePeriod().getStart()) : extractEffectiveDateTime(o);
    }

    @Nonnull
    private String extractEffectiveEnd(@Nonnull final Observation o) {
        return o.hasEffectivePeriod() && o.getEffectivePeriod().hasEnd()
               ? formatDate(o.getEffectivePeriod().getEnd()) : extractEffectiveDateTime(o);
    }

    @Nonnull
    String extractEffectiveDateTime(@Nonnull final Observation o) {
        return o.hasEffectiveDateTimeType() ? formatDate(o.getEffectiveDateTimeType().getValue())
                                            : extractEffectiveInstant(o);

    }

    @Nonnull
    String extractEffectiveInstant(@Nonnull final Observation o) {
        final var suffix1 = o.hasIssued() ? formatDate(o.getIssued()) : "unknown";

        return o.hasEffectiveInstantType() ? formatDate(o.getEffectiveInstantType().getValue())
                                           : suffix1;

    }

    @Nonnull
    private String extractPatientId(@Nonnull final Observation o) {
        return ofNullable(o.getSubject())
                .map(ref -> ref.getReferenceElement().getIdPart())
                .orElse(UNKNOWN);
    }

    @Nonnull
    private String extractId(@Nonnull final Observation o) {
        return ofNullable(o.getIdElement())
                .map(IdType::getIdPart)
                .orElse(UNKNOWN);
    }
}
