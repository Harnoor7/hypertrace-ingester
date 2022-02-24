package org.hypertrace.core.spannormalizer.jaeger;

import static org.hypertrace.core.spannormalizer.constants.SpanNormalizerConstants.SPAN_NORMALIZER_JOB_CONFIG;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.util.Timestamps;
import com.typesafe.config.Config;
import io.jaegertracing.api_v2.JaegerSpanInternalModel;
import io.jaegertracing.api_v2.JaegerSpanInternalModel.Span;
import io.micrometer.core.instrument.Counter;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.hypertrace.core.serviceframework.metrics.PlatformMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaegerSpanPreProcessor
    implements Transformer<byte[], Span, KeyValue<byte[], PreProcessedSpan>> {

  static final String SPANS_COUNTER = "hypertrace.reported.spans";
  private static final String DROPPED_SPANS_COUNTER = "hypertrace.reported.spans.dropped";
  private static final String IS_LATE_ARRIVAL_SPANS_TAGS = "is_late_arrival_spans";
  private static final String LATE_ARRIVAL_THRESHOLD_CONFIG_KEY =
      "processor.late.arrival.threshold.duration";
  private static final Logger LOG = LoggerFactory.getLogger(JaegerSpanPreProcessor.class);
  private static final ConcurrentMap<String, Counter> statusToSpansCounter =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Counter> tenantToSpansDroppedCount =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Counter> tenantToLateArrivalSpansDroppedCount =
      new ConcurrentHashMap<>();
  private static final Duration minArrivalThreshold = Duration.of(30, ChronoUnit.SECONDS);
  private TenantIdHandler tenantIdHandler;
  private SpanFilter spanFilter;
  private Duration lateArrivalThresholdDuration;

  public JaegerSpanPreProcessor() {
    // empty constructor
  }

  // constructor for testing
  JaegerSpanPreProcessor(Config jobConfig) {
    tenantIdHandler = new TenantIdHandler(jobConfig);
    spanFilter = new SpanFilter(jobConfig);
    lateArrivalThresholdDuration = configureLateArrivalThreshold(jobConfig);
  }

  @Override
  public void init(ProcessorContext context) {
    Config jobConfig = (Config) context.appConfigs().get(SPAN_NORMALIZER_JOB_CONFIG);
    tenantIdHandler = new TenantIdHandler(jobConfig);
    spanFilter = new SpanFilter(jobConfig);
    lateArrivalThresholdDuration = configureLateArrivalThreshold(jobConfig);
  }

  private Duration configureLateArrivalThreshold(Config jobConfig) {
    Duration configuredThreshold = jobConfig.getDuration(LATE_ARRIVAL_THRESHOLD_CONFIG_KEY);
    if (minArrivalThreshold.compareTo(configuredThreshold) > 0) {
      throw new IllegalArgumentException(
          "the value of " + "processor.late.arrival.threshold.duration should be higher than 30s");
    }
    return configuredThreshold;
  }

  @Override
  public KeyValue<byte[], PreProcessedSpan> transform(byte[] key, Span value) {
    try {
      // this is total spans count received. Irrespective of the fact we are able to parse them, or
      // they have tenantId or not.
      statusToSpansCounter
          .computeIfAbsent(
              "received",
              k -> PlatformMetricsRegistry.registerCounter(SPANS_COUNTER, Map.of("result", k)))
          .increment();

      PreProcessedSpan preProcessedSpan = preProcessSpan(value);

      if (null == preProcessedSpan) {
        statusToSpansCounter
            .computeIfAbsent(
                "dropped",
                k -> PlatformMetricsRegistry.registerCounter(SPANS_COUNTER, Map.of("result", k)))
            .increment();
        return null;
      }

      return new KeyValue<>(key, preProcessedSpan);
    } catch (Exception e) {
      LOG.error("Error preprocessing span", e);
      statusToSpansCounter
          .computeIfAbsent(
              "error",
              k -> PlatformMetricsRegistry.registerCounter(SPANS_COUNTER, Map.of("result", k)))
          .increment();
      return null;
    }
  }

  @VisibleForTesting
  PreProcessedSpan preProcessSpan(Span span) {
    Map<String, JaegerSpanInternalModel.KeyValue> spanTags =
        span.getTagsList().stream()
            .collect(Collectors.toMap(t -> t.getKey().toLowerCase(), t -> t, (v1, v2) -> v2));
    Map<String, JaegerSpanInternalModel.KeyValue> processTags =
        span.getProcess().getTagsList().stream()
            .collect(Collectors.toMap(t -> t.getKey().toLowerCase(), t -> t, (v1, v2) -> v2));

    Optional<String> maybeTenantId =
        tenantIdHandler.getAllowedTenantId(span, spanTags, processTags);
    if (maybeTenantId.isEmpty()) {
      return null;
    }

    String tenantId = maybeTenantId.get();

    if (spanFilter.shouldDropSpan(span, spanTags, processTags)) {
      // increment dropped counter at tenant level
      tenantToSpansDroppedCount
          .computeIfAbsent(
              tenantId,
              tenant ->
                  PlatformMetricsRegistry.registerCounter(
                      DROPPED_SPANS_COUNTER, Map.of("tenantId", tenantId)))
          .increment();
      return null;
    }

    // drop the span if the arrival time of it too old than configured threshold
    long spanProcessedTime = System.currentTimeMillis();
    long spanStartTime = Timestamps.toMillis(span.getStartTime());
    Duration spanArrivalDelay =
        Duration.of(Math.abs(spanProcessedTime - spanStartTime), ChronoUnit.MILLIS);

    if (spanStartTime > 0 && spanArrivalDelay.compareTo(lateArrivalThresholdDuration) > 0) {
      tenantToLateArrivalSpansDroppedCount
          .computeIfAbsent(
              tenantId,
              tenant ->
                  PlatformMetricsRegistry.registerCounter(
                      DROPPED_SPANS_COUNTER,
                      Map.of("tenantId", tenantId, IS_LATE_ARRIVAL_SPANS_TAGS, "true")))
          .increment();
      return null;
    }

    return new PreProcessedSpan(tenantId, span);
  }

  @Override
  public void close() {
    // noop
  }
}
