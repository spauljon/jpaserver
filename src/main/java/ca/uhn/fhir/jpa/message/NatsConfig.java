package ca.uhn.fhir.jpa.message;

import ca.uhn.fhir.interceptor.api.IInterceptorService;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

import static io.nats.client.Nats.connect;

@Configuration
public class NatsConfig {
    private final IInterceptorService interceptorService;

    private final JetStream jetStream;

    private final String subject;

    public NatsConfig(@Nonnull final IInterceptorService interceptorService,
                      @Value("${nats.subject}") String subject,
                      @Value("${nats.url}") String url,
                      @Value("${nats.stream}") String stream)
            throws IOException, InterruptedException, JetStreamApiException {
        this.interceptorService = interceptorService;
        this.jetStream = getJetStream(connect(url), stream);

        this.subject = subject;
    }

    @PostConstruct
    public void natsMessageInterceptor() {
        interceptorService.registerInterceptor(new NatsMessageInterceptor(jetStream, subject));
    }

    private JetStream getJetStream(@Nonnull final Connection conn,
                                   @Nonnull final String stream)
            throws IOException, JetStreamApiException {
        final var jsm = conn.jetStreamManagement();

        jsm.getStreamInfo(stream);

        return conn.jetStream();
    }

}
