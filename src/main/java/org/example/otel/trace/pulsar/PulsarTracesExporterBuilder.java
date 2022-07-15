package org.example.otel.trace.pulsar;

import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.impl.auth.AuthenticationDisabled;

public class PulsarTracesExporterBuilder {

    public static PulsarTracesExporterBuilder builder() {
        return new PulsarTracesExporterBuilder();
    }

    private String topic = "persistent://public/default/otlp_spans";
    private String serviceURL = "pulsar://localhost:5560";
    private boolean enableBatch = true;
    private Authentication auth = AuthenticationDisabled.INSTANCE;


    private PulsarTracesExporterBuilder() {
    }

    public PulsarTracesExporterBuilder withTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public PulsarTracesExporterBuilder withServiceURL(String serviceURL) {
        this.serviceURL = serviceURL;
        return this;
    }

    public PulsarTracesExporterBuilder withEnableBatch(boolean enableBatch) {
        this.enableBatch = enableBatch;
        return this;
    }

    public PulsarTracesExporterBuilder withAuthentication(Authentication auth) {
        this.auth = auth;
        return this;
    }


    public PulsarTracesExporter build() {
        try {
            var client = PulsarClient.builder()
                    .serviceUrl(serviceURL)
                    .authentication(this.auth)
                    .build();

            var producer = client.newProducer(Schema.BYTES)
                    .topic(this.topic)
                    .enableBatching(this.enableBatch)
                    .create();

            return new PulsarTracesExporter(client, producer);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
