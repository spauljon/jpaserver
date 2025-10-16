package ca.uhn.fhir.jpa.message;

import ca.uhn.fhir.broker.api.ChannelProducerSettings;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.subscription.channel.impl.LinkedBlockingChannelFactory;
import ca.uhn.fhir.jpa.subscription.model.ResourceModifiedMessage;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.SubscribableChannel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Optional;

import static ca.uhn.fhir.context.FhirContext.forR4;

@Configuration
public class NatsConfig {
    private final FhirContext fhirContext = forR4();

    @Bean
    public SubscribableChannel fhirObservation(
            @Nonnull final LinkedBlockingChannelFactory factory,
            @Value("${nats.channel}") String channel) {
        return Optional.of(factory.getOrCreateProducer(
                               channel,
                               new ChannelProducerSettings() {
                                   @Override
                                   public Integer getConcurrentConsumers() {
                                       return 4;
                                   }
                               }))
                       .filter(SubscribableChannel.class::isInstance)
                       .map(c -> (SubscribableChannel) c)
                       .orElseThrow();
    }

    @Bean(destroyMethod = "close")
    public Connection natsConnection(@Value("${nats.url}") String url)
            throws IOException, InterruptedException {
        final var opts = new Options.Builder()
                .server(url)
                .connectionName("hapi-fhir-message-bridge")
                .build();
        return Nats.connect(opts);
    }

    @Bean
    public JetStream jetStream(@Nonnull final Connection nats) throws IOException {
        return nats.jetStream();
    }

    /**
     * Subscribe a simple MessageHandler to the channel (no Spring Integration).
     * It publishes the payload to JetStream.
     */
    @Bean
    public InitializingBean bindFhirObservationToNats(
            @Nonnull @Qualifier("fhirObservation") final SubscribableChannel channel,
            @Nonnull final JetStream js,
            @Value("${nats.subject}") String subject) {
        final var publisher = new ObservationPublisher(js, subject);

        return () -> channel.subscribe(message -> {
            Optional.of(message.getPayload())
                    .filter(ResourceModifiedMessage.class::isInstance)
                    .map(ResourceModifiedMessage.class::cast)
                    .map(p -> p.getResource(fhirContext))
                    .filter(Observation.class::isInstance)
                    .map(Observation.class::cast)
                    .ifPresent(o -> publisher
                            .publish(o, ((ResourceModifiedMessage) message.getPayload())
                                    .getOperationType()));
        });
    }
}
