package ca.uhn.fhir.jpa.message;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.subscription.model.CanonicalSubscription;
import ca.uhn.fhir.jpa.subscription.model.ResourceDeliveryMessage;
import io.nats.client.JetStream;
import jakarta.annotation.Nonnull;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static java.util.Optional.ofNullable;

@Interceptor
public class NatsMessageInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NatsMessageInterceptor.class);

    private static final String NATS_SUBSCRIPTION = "nats-observation-subscription";

    private final ObservationPublisher publisher;

    public NatsMessageInterceptor(@Nonnull final JetStream jetStream,
                                  @Nonnull final String subject) {
        this.publisher = new ObservationPublisher(jetStream, subject);
    }

    @Hook(Pointcut.SUBSCRIPTION_BEFORE_MESSAGE_DELIVERY)
    public boolean publishObservation(@Nonnull final CanonicalSubscription subscription,
                                      @Nonnull final ResourceDeliveryMessage message) {
        if (NATS_SUBSCRIPTION.equals(subscription.getIdPart())) {
            ofNullable(message.getPayload(forR4()))
                    .filter(Observation.class::isInstance)
                    .map(Observation.class::cast)
                    .ifPresent(o -> publisher.publish(o, message.getOperationType()));

            LOGGER.info("NatsMessageInterceptor: delivered message for subscription {}",
                        subscription.getIdPart());

            return false;
        }

        return true;
    }
}
