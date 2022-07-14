package org.example.otel.trace.otlp;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.IdGenerator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;


@Slf4j
public class OtlpExporterTest {

    private static final String INSTRUMENTATION_NAME = "otlp-2pipelines";
//    private static final String INSTRUMENTATION_NAME = "otlp-simple";
    private static final int MAX_TRACES = 200;
    private static final int MAX_SPANS = 10;

    @BeforeAll
    public static void setup() {
        var baggage = W3CBaggagePropagator.getInstance();
        var trace = W3CTraceContextPropagator.getInstance();

        var propagator = TextMapPropagator.composite(trace, baggage);
        var propagators = ContextPropagators.create(propagator);

        var exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:4317")
                .build();

        var processor = BatchSpanProcessor.builder(exporter)
                .setExporterTimeout(Duration.ofSeconds(5))
                .setMaxExportBatchSize(100)
                .build();

        var attributes = Attributes.of(
                AttributeKey.stringKey("service.name"), INSTRUMENTATION_NAME,
                AttributeKey.stringKey("service.env"), "dev");

        var provider = SdkTracerProvider.builder()
                .setClock(Clock.getDefault())
                .setIdGenerator(IdGenerator.random())
                .setSpanLimits(SpanLimits.getDefault())
                .setSampler(Sampler.alwaysOn())
                .setResource(Resource.create(attributes))
                .addSpanProcessor(processor)
                .build();

        OpenTelemetrySdk.builder()
                .setPropagators(propagators)
                .setTracerProvider(provider)
                .buildAndRegisterGlobal();

        log.info("Open Telemetry Completing...");
    }

    @Test
    public void testSendTraces() throws Exception {
        var tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
        for (var i = 0; i < MAX_TRACES; i++) {
            var parent = tracer.spanBuilder(INSTRUMENTATION_NAME + "_trace_" + i)
                    .setSpanKind(SpanKind.INTERNAL)
                    .setNoParent()
                    .startSpan();

            try (var _ignore = parent.makeCurrent()) {
                for (var j = 0; j < MAX_SPANS; j++) {
                    var child = tracer.spanBuilder("Child_" + j)
                            .setParent(Context.current())
                            .setSpanKind(SpanKind.INTERNAL)
                            .startSpan();

                    Thread.sleep(1);
                    child.end();
                }
            }
            parent.end();
        }

        log.info("Test Finished...");
    }

}
