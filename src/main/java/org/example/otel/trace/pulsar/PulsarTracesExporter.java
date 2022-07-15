package org.example.otel.trace.pulsar;

import io.opentelemetry.exporter.internal.grpc.MarshalerInputStream;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;

import java.io.IOException;
import java.util.Collection;

@Slf4j
public record PulsarTracesExporter(PulsarClient client,
                                   Producer<byte[]> producer) implements SpanExporter {

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        var request = TraceRequestMarshaler.create(spans);
        var input = new MarshalerInputStream(request);
        byte[] value;
        try {
            value = input.readAllBytes();
        } catch (IOException e) {
            log.error("Read all bytes failed", e);
            return CompletableResultCode.ofFailure();
        }

        var result = new CompletableResultCode();
        producer.newMessage()
                .value(value)
                .sendAsync()
                .whenComplete((__, t) -> {
                    if (t == null) {
                        result.succeed();
                    } else {
                        result.fail();
                    }
                });

        return result;
    }

    @Override
    public CompletableResultCode flush() {
        var result = new CompletableResultCode();
        this.producer.flushAsync()
                .whenComplete((__, t) -> {
                    if (null == t) {
                        result.succeed();
                    } else {
                        result.fail();
                    }
                });

        return result;
    }

    @Override
    public CompletableResultCode shutdown() {
        var result = new CompletableResultCode();
        this.producer.closeAsync()
                .thenApply(unused -> this.client.closeAsync())
                .thenAccept(__ -> result.succeed())
                .exceptionally(__ -> {
                    result.fail();
                    return null;
                });

        return result;
    }
}
