package org.hypertrace.traceenricher.enrichedspan.constants.utils;

import static com.google.common.net.HttpHeaders.COOKIE;
import static com.google.common.net.HttpHeaders.SET_COOKIE;
import static org.hypertrace.core.span.constants.v1.Grpc.GRPC_REQUEST_METADATA;
import static org.hypertrace.core.span.constants.v1.Grpc.GRPC_RESPONSE_METADATA;
import static org.hypertrace.core.span.constants.v1.Http.HTTP_REQUEST_HEADER;
import static org.hypertrace.core.span.constants.v1.Http.HTTP_RESPONSE_HEADER;
import static org.hypertrace.core.span.normalizer.constants.RpcSpanTag.RPC_REQUEST_METADATA;
import static org.hypertrace.core.span.normalizer.constants.RpcSpanTag.RPC_RESPONSE_METADATA;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.avro.reflect.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.hypertrace.core.datamodel.AttributeValue;
import org.hypertrace.core.datamodel.Event;
import org.hypertrace.core.datamodel.shared.SpanAttributeUtils;
import org.hypertrace.core.semantic.convention.constants.http.OTelHttpSemanticConventions;
import org.hypertrace.core.span.constants.RawSpanConstants;
import org.hypertrace.core.span.constants.v1.TracerAttribute;
import org.hypertrace.entity.constants.v1.ApiAttribute;
import org.hypertrace.entity.constants.v1.BackendAttribute;
import org.hypertrace.entity.constants.v1.K8sEntityAttribute;
import org.hypertrace.entity.constants.v1.ServiceAttribute;
import org.hypertrace.entity.service.constants.EntityConstants;
import org.hypertrace.semantic.convention.utils.http.HttpSemanticConventionUtils;
import org.hypertrace.semantic.convention.utils.rpc.RpcSemanticConventionUtils;
import org.hypertrace.traceenricher.enrichedspan.constants.EnrichedSpanConstants;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.Api;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.Backend;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.BoundaryTypeValue;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.CommonAttribute;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.Http;
import org.hypertrace.traceenricher.enrichedspan.constants.v1.Protocol;

/**
 * Utility class to easily read named attributes from an enriched span. This is equivalent of an
 * enriched span POJO.
 */
public class EnrichedSpanUtils {
  private static final String DOT = ".";
  private static final String SERVICE_ID_ATTR =
      EntityConstants.getValue(ServiceAttribute.SERVICE_ATTRIBUTE_ID);
  private static final String SERVICE_NAME_ATTR =
      EntityConstants.getValue(ServiceAttribute.SERVICE_ATTRIBUTE_NAME);

  private static final String API_ID_ATTR = EntityConstants.getValue(ApiAttribute.API_ATTRIBUTE_ID);
  private static final String API_URL_PATTERN_ATTR =
      EntityConstants.getValue(ApiAttribute.API_ATTRIBUTE_URL_PATTERN);
  private static final String API_NAME_ATTR =
      EntityConstants.getValue(ApiAttribute.API_ATTRIBUTE_NAME);
  private static final String API_DISCOVERY_STATE_ATTR =
      EntityConstants.getValue(ApiAttribute.API_ATTRIBUTE_DISCOVERY_STATE);

  private static final String NAMESPACE_NAME_ATTR =
      EntityConstants.getValue(K8sEntityAttribute.K8S_ENTITY_ATTRIBUTE_NAMESPACE_NAME);
  private static final String CLUSTER_NAME_ATTR =
      EntityConstants.getValue(K8sEntityAttribute.K8S_ENTITY_ATTRIBUTE_CLUSTER_NAME);

  private static final String BACKEND_ID_ATTR =
      EntityConstants.getValue(BackendAttribute.BACKEND_ATTRIBUTE_ID);
  private static final String BACKEND_NAME_ATTR =
      EntityConstants.getValue(BackendAttribute.BACKEND_ATTRIBUTE_NAME);
  private static final String BACKEND_HOST_ATTR =
      EntityConstants.getValue(BackendAttribute.BACKEND_ATTRIBUTE_HOST);
  private static final String BACKEND_PORT_ATTR =
      EntityConstants.getValue(BackendAttribute.BACKEND_ATTRIBUTE_PORT);
  private static final String BACKEND_PROTOCOL_ATTR =
      EntityConstants.getValue(BackendAttribute.BACKEND_ATTRIBUTE_PROTOCOL);
  private static final String BACKEND_PATH_ATTR =
      EntityConstants.getValue(BackendAttribute.BACKEND_ATTRIBUTE_PATH);
  private static final String BACKEND_OPERATION_ATTR =
      EnrichedSpanConstants.getValue(Backend.BACKEND_OPERATION);
  private static final String BACKEND_DESTINATION_ATTR =
      EnrichedSpanConstants.getValue(Backend.BACKEND_DESTINATION);

  private static final String SPAN_TYPE_ATTR =
      EnrichedSpanConstants.getValue(CommonAttribute.COMMON_ATTRIBUTE_SPAN_TYPE);
  private static final String API_BOUNDARY_TYPE_ATTR =
      EnrichedSpanConstants.getValue(Api.API_BOUNDARY_TYPE);
  private static final String TRACER_TYPE_ATTR =
      RawSpanConstants.getValue(TracerAttribute.TRACER_ATTRIBUTE_TRACER_TYPE);
  private static final String PROTOCOL_ATTR =
      EnrichedSpanConstants.getValue(CommonAttribute.COMMON_ATTRIBUTE_PROTOCOL);
  private static final String HOST_HEADER_ATTR = EnrichedSpanConstants.getValue(Http.HTTP_HOST);

  private static final String HTTP_USER_AGENT =
      RawSpanConstants.getValue(org.hypertrace.core.span.constants.v1.Http.HTTP_USER_AGENT);
  private static final String USER_AGENT =
      RawSpanConstants.getValue(org.hypertrace.core.span.constants.v1.Http.HTTP_USER_DOT_AGENT);
  private static final String USER_AGENT_UNDERSCORE =
      RawSpanConstants.getValue(
          org.hypertrace.core.span.constants.v1.Http.HTTP_USER_AGENT_WITH_UNDERSCORE);
  private static final String USER_AGENT_DASH =
      RawSpanConstants.getValue(
          org.hypertrace.core.span.constants.v1.Http.HTTP_USER_AGENT_WITH_DASH);
  private static final String USER_AGENT_REQUEST_HEADER =
      RawSpanConstants.getValue(
          org.hypertrace.core.span.constants.v1.Http.HTTP_USER_AGENT_REQUEST_HEADER);
  private static final String OTEL_HTTP_USER_AGENT =
      OTelHttpSemanticConventions.HTTP_USER_AGENT.getValue();
  private static final String HTTP_RESPONSE_HEADER_PREFIX =
      RawSpanConstants.getValue(HTTP_RESPONSE_HEADER) + DOT;
  private static final String HTTP_REQUEST_HEADER_PREFIX =
      RawSpanConstants.getValue(HTTP_REQUEST_HEADER) + DOT;
  private static final String GRPC_REQUEST_METADATA_PREFIX =
      RawSpanConstants.getValue(GRPC_REQUEST_METADATA) + DOT;
  private static final String RPC_REQUEST_METADATA_PREFIX = RPC_REQUEST_METADATA.getValue() + DOT;
  private static final String GRPC_RESPONSE_METADATA_PREFIX =
      RawSpanConstants.getValue(GRPC_RESPONSE_METADATA) + DOT;
  private static final String RPC_RESPONSE_METADATA_PREFIX = RPC_RESPONSE_METADATA.getValue() + DOT;
  private static final String RESPONSE_COOKIE_HEADER_PREFIX =
      HTTP_RESPONSE_HEADER_PREFIX + SET_COOKIE.toLowerCase();
  private static final String REQUEST_COOKIE_HEADER_KEY =
      HTTP_REQUEST_HEADER_PREFIX + COOKIE.toLowerCase();

  @VisibleForTesting
  static final List<String> USER_AGENT_ATTRIBUTES =
      ImmutableList.of(
          USER_AGENT,
          USER_AGENT_UNDERSCORE,
          USER_AGENT_DASH,
          USER_AGENT_REQUEST_HEADER,
          HTTP_USER_AGENT,
          OTEL_HTTP_USER_AGENT);

  @Nullable
  public static String getStringAttribute(Event event, String attributeKey) {
    AttributeValue value = SpanAttributeUtils.getAttributeValue(event, attributeKey);
    return value == null ? null : value.getValue();
  }

  public static Protocol getProtocol(Event event) {
    String protocol = getStringAttribute(event, PROTOCOL_ATTR);
    if (protocol != null) {
      for (Protocol p : Protocol.values()) {
        if (p != Protocol.UNRECOGNIZED && EnrichedSpanConstants.getValue(p).equals(protocol)) {
          return p;
        }
      }
    }
    return null;
  }

  public static Protocol getProtocol(Event.Builder eventBuilder) {
    String protocol = SpanAttributeUtils.getStringAttribute(eventBuilder, PROTOCOL_ATTR);
    if (protocol != null) {
      for (Protocol p : Protocol.values()) {
        if (p != Protocol.UNRECOGNIZED && EnrichedSpanConstants.getValue(p).equals(protocol)) {
          return p;
        }
      }
    }
    return null;
  }

  public static String getServiceId(Event event) {
    return getStringAttribute(event, SERVICE_ID_ATTR);
  }

  public static String getServiceName(Event event) {
    return getStringAttribute(event, SERVICE_NAME_ATTR);
  }

  public static String getBackendId(Event event) {
    return getStringAttribute(event, BACKEND_ID_ATTR);
  }

  public static String getBackendName(Event event) {
    return getStringAttribute(event, BACKEND_NAME_ATTR);
  }

  public static String getBackendHost(Event event) {
    return getStringAttribute(event, BACKEND_HOST_ATTR);
  }

  public static String getBackendPort(Event event) {
    return getStringAttribute(event, BACKEND_PORT_ATTR);
  }

  public static String getBackendPath(Event event) {
    return getStringAttribute(event, BACKEND_PATH_ATTR);
  }

  @Nullable
  public static String getBackendOperation(Event event) {
    return getStringAttribute(event, BACKEND_OPERATION_ATTR);
  }

  @Nullable
  public static String getBackendDestination(Event event) {
    return getStringAttribute(event, BACKEND_DESTINATION_ATTR);
  }

  public static String getBackendProtocol(Event event) {
    return getStringAttribute(event, BACKEND_PROTOCOL_ATTR);
  }

  public static String getNamespaceName(Event event) {
    return getStringAttribute(event, NAMESPACE_NAME_ATTR);
  }

  public static String getApiId(Event event) {
    return getStringAttribute(event, API_ID_ATTR);
  }

  public static String getApiPattern(Event event) {
    return getStringAttribute(event, API_URL_PATTERN_ATTR);
  }

  public static String getApiName(Event event) {
    return getStringAttribute(event, API_NAME_ATTR);
  }

  public static String getApiDiscoveryState(Event event) {
    return getStringAttribute(event, API_DISCOVERY_STATE_ATTR);
  }

  public static boolean isExternalApi(Event e) {
    return SpanAttributeUtils.getBooleanAttribute(
        e, EntityConstants.getValue(ApiAttribute.API_ATTRIBUTE_IS_EXTERNAL_API));
  }

  public static String getSpanType(Event event) {
    return getStringAttribute(event, SPAN_TYPE_ATTR);
  }

  public static String getTracerType(Event event) {
    return getStringAttribute(event, TRACER_TYPE_ATTR);
  }

  @Nullable
  public static String getApiBoundaryType(Event event) {
    return getStringAttribute(event, API_BOUNDARY_TYPE_ATTR);
  }

  /** Find the First Span (Entrance Span) of the Api Trace and return its id */
  @Nullable
  public static ByteBuffer getApiEntrySpanId(
      Event event, Map<ByteBuffer, Event> idToEvent, Map<ByteBuffer, ByteBuffer> childToParent) {
    Event entryApiEvent = getApiEntrySpan(event, idToEvent, childToParent);
    if (entryApiEvent != null) {
      return entryApiEvent.getEventId();
    }
    return null;
  }

  /** Helper method to find and entryApiEvent by iterate parent-child chain. */
  @Nullable
  public static Event getApiEntrySpan(
      Event event, Map<ByteBuffer, Event> idToEvent, Map<ByteBuffer, ByteBuffer> childToParent) {
    String apiBoundary = getApiBoundaryType(event);
    if (EnrichedSpanConstants.getValue(BoundaryTypeValue.BOUNDARY_TYPE_VALUE_ENTRY)
        .equals(apiBoundary)) {
      // if current span itself is an api entry span, return same.
      return event;
    } else {
      // current span is not an api entry span, find an ancestor who is an api entry span
      Event parentEvent = idToEvent.get(childToParent.get(event.getEventId()));
      while (parentEvent != null) {
        apiBoundary = getApiBoundaryType(parentEvent);
        if (EnrichedSpanConstants.getValue(BoundaryTypeValue.BOUNDARY_TYPE_VALUE_ENTRY)
            .equals(apiBoundary)) {
          return parentEvent;
        }
        parentEvent = idToEvent.get(childToParent.get(parentEvent.getEventId()));
      }
    }
    // oops, we didn't find the any api entry span in the parent-child chain
    return null;
  }

  public static boolean isEntryApiBoundary(Event event) {
    return EnrichedSpanConstants.getValue(BoundaryTypeValue.BOUNDARY_TYPE_VALUE_ENTRY)
        .equalsIgnoreCase(getApiBoundaryType(event));
  }

  public static boolean isExitSpan(Event event) {
    if (event == null) {
      return false;
    }
    return EnrichedSpanConstants.getValue(BoundaryTypeValue.BOUNDARY_TYPE_VALUE_EXIT)
        .equalsIgnoreCase(getSpanType(event));
  }

  public static boolean isEntrySpan(Event event) {
    if (event == null) {
      return false;
    }
    return EnrichedSpanConstants.getValue(BoundaryTypeValue.BOUNDARY_TYPE_VALUE_ENTRY)
        .equalsIgnoreCase(getSpanType(event));
  }

  public static boolean isExitApiBoundary(Event event) {
    return EnrichedSpanConstants.getValue(BoundaryTypeValue.BOUNDARY_TYPE_VALUE_EXIT)
        .equalsIgnoreCase(getApiBoundaryType(event));
  }

  public static String getClusterName(Event span) {
    return getStringAttribute(span, CLUSTER_NAME_ATTR);
  }

  public static String getHostHeader(Event span) {
    return getStringAttribute(span, HOST_HEADER_ATTR);
  }

  public static boolean containsServiceId(Event span) {
    return SpanAttributeUtils.containsAttributeKey(span, SERVICE_ID_ATTR);
  }

  @Nullable
  public static String getStatus(Event event) {
    return getStringAttribute(event, EnrichedSpanConstants.getValue(Api.API_STATUS));
  }

  @Nullable
  public static String getStatusCode(Event event) {
    return getStringAttribute(event, EnrichedSpanConstants.getValue(Api.API_STATUS_CODE));
  }

  @Nullable
  public static String getStatusMessage(Event event) {
    return getStringAttribute(event, EnrichedSpanConstants.getValue(Api.API_STATUS_MESSAGE));
  }

  @Nullable
  public static String getUserAgent(Event event) {
    return SpanAttributeUtils.getFirstAvailableStringAttribute(event, USER_AGENT_ATTRIBUTES);
  }

  public static Optional<String> getHttpMethod(Event event) {
    return HttpSemanticConventionUtils.getHttpMethod(event);
  }

  public static Optional<String> getFullHttpUrl(Event event) {
    return HttpSemanticConventionUtils.getFullHttpUrl(event);
  }

  public static Optional<String> getDestinationIpAddress(Event event) {
    return HttpSemanticConventionUtils.getDestinationIpAddress(event);
  }

  public static Optional<String> getPath(Event event) {
    return HttpSemanticConventionUtils.getHttpPath(event);
  }

  public static Optional<String> getQueryString(Event event) {
    return HttpSemanticConventionUtils.getHttpQueryString(event);
  }

  public static Optional<Integer> getRequestSize(Event event) {
    Protocol protocol = EnrichedSpanUtils.getProtocol(event);
    if (protocol == null) {
      return Optional.empty();
    }

    switch (protocol) {
      case PROTOCOL_HTTP:
      case PROTOCOL_HTTPS:
        return HttpSemanticConventionUtils.getHttpRequestSize(event);
      case PROTOCOL_GRPC:
        return RpcSemanticConventionUtils.getGrpcRequestSize(event);
    }

    return Optional.empty();
  }

  public static Optional<Integer> getResponseSize(Event event) {
    Protocol protocol = EnrichedSpanUtils.getProtocol(event);
    if (protocol == null) {
      return Optional.empty();
    }

    switch (protocol) {
      case PROTOCOL_HTTP:
      case PROTOCOL_HTTPS:
        return HttpSemanticConventionUtils.getHttpResponseSize(event);
      case PROTOCOL_GRPC:
        return RpcSemanticConventionUtils.getGrpcResponseSize(event);
    }

    return Optional.empty();
  }

  public static List<String> getSpaceIds(Event event) {
    return Optional.ofNullable(
            SpanAttributeUtils.getAttributeValue(event, EnrichedSpanConstants.SPACE_IDS_ATTRIBUTE))
        .map(AttributeValue::getValueList)
        .orElseGet(Collections::emptyList);
  }

  /**
   * The request headers are populated as key-value pair
   *
   * @return map of headers with identifying prefix removed key -> value
   *     <ul>
   *       <li>header1 -> value1
   *       <li>header2 -> value2
   *       <li>header3 -> value3
   *     </ul>
   */
  public static Map<String, String> getRequestHeadersExceptCookies(Event event) {
    Map<String, String> requestHeadersExceptCookies = new HashMap<>();

    // To get http request headers except cookies we use http.request.header. as prefix
    requestHeadersExceptCookies.putAll(
        HttpSemanticConventionUtils.getHttpHeadersExceptCookies(
            event,
            entry -> !HttpSemanticConventionUtils.isHttpRequestCookie(entry.getKey()),
            HTTP_REQUEST_HEADER_PREFIX));

    // To get grpc request headers except cookies we use grpc.request.metadata. as prefix
    requestHeadersExceptCookies.putAll(
        RpcSemanticConventionUtils.getGrpcHeadersExceptCookies(
            event, GRPC_REQUEST_METADATA_PREFIX));

    // To get rpc request headers except cookies we use rpc.request.metadata. as prefix
    requestHeadersExceptCookies.putAll(
        RpcSemanticConventionUtils.getRpcHeadersExceptCookies(event, RPC_REQUEST_METADATA_PREFIX));
    return Collections.unmodifiableMap(requestHeadersExceptCookies);
  }

  /**
   * The response headers are populated as key-value pair
   *
   * @return map of headers with identifying prefix removed key -> value
   *     <ul>
   *       <li>header1 -> value1
   *       <li>header2 -> value2
   *       <li>header3 -> value3
   *     </ul>
   */
  public static Map<String, String> getResponseHeadersExceptCookies(Event event) {
    Map<String, String> responseHeadersExceptCookies = new HashMap<>();

    // To get http response headers except cookies we use http.response.header. as prefix
    responseHeadersExceptCookies.putAll(
        HttpSemanticConventionUtils.getHttpHeadersExceptCookies(
            event,
            entry -> !HttpSemanticConventionUtils.isHttpResponseCookie(entry.getKey()),
            HTTP_RESPONSE_HEADER_PREFIX));

    // To get grpc response headers except cookies we use grpc.response.metadata. as prefix
    responseHeadersExceptCookies.putAll(
        RpcSemanticConventionUtils.getGrpcHeadersExceptCookies(
            event, GRPC_RESPONSE_METADATA_PREFIX));

    // To get rpc response headers except cookies we use rpc.response.metadata. as prefix
    responseHeadersExceptCookies.putAll(
        RpcSemanticConventionUtils.getRpcHeadersExceptCookies(event, RPC_RESPONSE_METADATA_PREFIX));
    return Collections.unmodifiableMap(responseHeadersExceptCookies);
  }

  /**
   * The request cookies are populated as `http.request.header.cookie` with value as
   * cookie1=value1;cookie2=value2;cookie3=value3
   *
   * @return map of cookie key -> value
   *     <ul>
   *       <li>cookie1 -> value1
   *       <li>cookie2 -> value2
   *       <li>cookie3 -> value3
   *     </ul>
   */
  public static Map<String, String> getHttpRequestCookies(Event event) {
    return HttpSemanticConventionUtils.getHttpRequestCookies(event);
  }

  /**
   * The response cookies are populated as `http.response.header.set-cookie[0] -> cookie1=value1`,
   * `http.response.header.set-cookie[1] -> cookie2=value2` and so on in span attributes
   *
   * @return map of cookie key -> value *
   *     <ul>
   *       *
   *       <li>cookie1 -> value1
   *       <li>cookie2 -> value2
   *     </ul>
   */
  public static Map<String, String> getHttpResponseCookies(Event event) {
    return HttpSemanticConventionUtils.getHttpResponseCookies(event);
  }

  /** Check whether these spans belongs to different services. */
  public static boolean areBothSpansFromDifferentService(Event event, Event parentEvent) {
    if (event.getServiceName() == null || parentEvent.getServiceName() == null) {
      return false;
    }
    return !StringUtils.equals(event.getServiceName(), parentEvent.getServiceName());
  }
}
