package swagger2sqlmap.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import swagger2sqlmap.model.EndpointRow;

import java.io.File;
import java.net.URI;
import java.util.*;

public final class SwaggerParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SwaggerParser() {}

  public static ParseResult parse(File jsonFile) throws Exception {
    JsonNode root = MAPPER.readTree(jsonFile);

    String baseUrl = detectBaseUrl(root);
    List<EndpointRow> endpoints = extractEndpoints(root);

    return new ParseResult(baseUrl, endpoints);
  }

  /**
   * OpenAPI 3:
   *   servers[0].url
   * Swagger 2:
   *   schemes[0]://host + basePath
   */
  public static String detectBaseUrl(JsonNode root) {
    if (root == null || root.isMissingNode() || root.isNull()) return "";

    // OpenAPI 3
    JsonNode servers = root.path("servers");
    if (servers.isArray() && servers.size() > 0) {
      String url = servers.get(0).path("url").asText("");
      url = normalizeBaseUrl(url);
      if (!url.isEmpty()) return url;
    }

    // Swagger 2
    String host = root.path("host").asText("");
    String basePath = root.path("basePath").asText("");
    String scheme = "";

    JsonNode schemes = root.path("schemes");
    if (schemes.isArray() && schemes.size() > 0) {
      scheme = schemes.get(0).asText("");
    }

    if (!host.isEmpty()) {
      if (scheme.isEmpty()) scheme = "https";
      String url = scheme + "://" + host + normalizePath(basePath);
      return normalizeBaseUrl(url);
    }

    return "";
  }

  public static List<EndpointRow> extractEndpoints(JsonNode root) {
    if (root == null || root.isMissingNode() || root.isNull()) return List.of();

    JsonNode paths = root.path("paths");
    if (!paths.isObject()) return List.of();

    boolean isOpenApi3 = root.has("openapi"); // OpenAPI 3.x marker

    List<EndpointRow> out = new ArrayList<>();

    Iterator<Map.Entry<String, JsonNode>> it = paths.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> p = it.next();
      String path = p.getKey();
      JsonNode methodsNode = p.getValue();
      if (!methodsNode.isObject()) continue;

      for (Iterator<Map.Entry<String, JsonNode>> mit = methodsNode.fields(); mit.hasNext(); ) {
        Map.Entry<String, JsonNode> m = mit.next();
        String method = m.getKey();
        JsonNode op = m.getValue();

        if (!isHttpMethod(method)) continue;

        String opId = op.path("operationId").asText("");
        String summary = op.path("summary").asText("");
        if (summary.isEmpty()) summary = op.path("description").asText("");

        BodyInfo body = isOpenApi3 ? extractBodyOpenApi3(root, op) : extractBodySwagger2(root, op);

        out.add(new EndpointRow(
            method.toUpperCase(Locale.ROOT),
            path,
            emptyToNull(opId),
            emptyToNull(summary),
            body.contentType(),
            body.template()
        ));
      }
    }

    out.sort(Comparator
        .comparing(EndpointRow::path, Comparator.nullsLast(String::compareTo))
        .thenComparing(EndpointRow::method, Comparator.nullsLast(String::compareTo)));

    return out;
  }

  private static boolean isHttpMethod(String m) {
    if (m == null) return false;
    return switch (m.toLowerCase(Locale.ROOT)) {
      case "get", "post", "put", "delete", "patch", "head", "options" -> true;
      default -> false;
    };
  }

  private static String normalizeBaseUrl(String s) {
    if (s == null) return "";
    s = s.trim();
    if (s.isEmpty()) return "";

    while (s.endsWith("/") && s.length() > "https://x".length()) {
      s = s.substring(0, s.length() - 1);
    }

    try {
      new URI(s);
    } catch (Exception ignored) {}

    return s;
  }

  private static String normalizePath(String p) {
    if (p == null || p.isBlank()) return "";
    p = p.trim();
    if (!p.startsWith("/")) p = "/" + p;
    if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
    return p;
  }

  private static String emptyToNull(String s) {
    if (s == null) return null;
    s = s.trim();
    return s.isEmpty() ? null : s;
  }

  // ================= Body extraction =================

  private record BodyInfo(String contentType, String template) {}

  private static BodyInfo extractBodyOpenApi3(JsonNode root, JsonNode op) {
    JsonNode rb = op.path("requestBody");
    if (rb.isMissingNode() || rb.isNull()) return new BodyInfo(null, null);

    JsonNode content = rb.path("content");
    if (!content.isObject()) return new BodyInfo(null, null);

    String ct = content.has("application/json") ? "application/json"
        : content.fieldNames().hasNext() ? content.fieldNames().next() : null;

    if (ct == null) return new BodyInfo(null, null);

    JsonNode media = content.path(ct);
    JsonNode schema = media.path("schema");
    if (schema.isMissingNode() || schema.isNull()) return new BodyInfo(ct, null);

    Object templateObj = buildValueFromOpenApi3Schema(root, schema, 0, new HashSet<>());
    String template = serializeBody(ct, templateObj);

    return new BodyInfo(ct, template);
  }

  private static BodyInfo extractBodySwagger2(JsonNode root, JsonNode op) {
    JsonNode params = op.path("parameters");
    if (!params.isArray() || params.size() == 0) return new BodyInfo(null, null);

    // 1) body schema
    for (JsonNode p : params) {
      if ("body".equalsIgnoreCase(p.path("in").asText(""))) {
        JsonNode schema = p.path("schema");
        String ct = pickConsumes(op, root);
        if (ct == null) ct = "application/json";

        Object obj = buildValueFromSwagger2Schema(root, schema, 0, new HashSet<>());
        String template = serializeBody(ct, obj);

        return new BodyInfo(ct, template);
      }
    }

    // 2) formData
    var form = new LinkedHashMap<String, Object>();
    for (JsonNode p : params) {
      if ("formData".equalsIgnoreCase(p.path("in").asText(""))) {
        String name = p.path("name").asText("");
        if (name.isEmpty()) continue;

        // Swagger2: type/format/example/default/enum
        form.put(name, scalarFromParam(p));
      }
    }
    if (!form.isEmpty()) {
      String ct = pickConsumes(op, root);
      if (ct == null) ct = "application/x-www-form-urlencoded";

      // form -> key=value&...
      if ("application/x-www-form-urlencoded".equalsIgnoreCase(ct)) {
        return new BodyInfo(ct, formUrlEncode(form));
      }

      // if they claim JSON even with formData, still JSON it
      return new BodyInfo(ct, serializeBody(ct, form));
    }

    return new BodyInfo(null, null);
  }

  // ================= Template builders (typed, no *) =================

  /**
   * OpenAPI 3 schema -> Java Object (Map/List/scalar)
   */
  private static Object buildValueFromOpenApi3Schema(JsonNode root,
                                                     JsonNode schema,
                                                     int depth,
                                                     Set<String> refStack) {
    if (schema == null || schema.isMissingNode() || schema.isNull()) return "text";

    // Prefer example/default/enum
    Object example = pickExample(schema);
    if (example != null) return example;

    // Resolve $ref
    if (schema.has("$ref")) {
      String ref = schema.path("$ref").asText("");
      String key = ref;
      if (!refStack.add(key)) return new LinkedHashMap<>(); // circular ref -> stop

      // "#/components/schemas/X"
      String name = lastRefName(ref);
      if (!name.isEmpty()) {
        JsonNode resolved = root.path("components").path("schemas").path(name);
        Object v = buildValueFromOpenApi3Schema(root, resolved, depth + 1, refStack);
        refStack.remove(key);
        return v;
      }
      refStack.remove(key);
    }

    // allOf/oneOf/anyOf (take first)
    JsonNode allOf = schema.path("allOf");
    if (allOf.isArray() && allOf.size() > 0) {
      return buildValueFromOpenApi3Schema(root, allOf.get(0), depth + 1, refStack);
    }
    JsonNode oneOf = schema.path("oneOf");
    if (oneOf.isArray() && oneOf.size() > 0) {
      return buildValueFromOpenApi3Schema(root, oneOf.get(0), depth + 1, refStack);
    }
    JsonNode anyOf = schema.path("anyOf");
    if (anyOf.isArray() && anyOf.size() > 0) {
      return buildValueFromOpenApi3Schema(root, anyOf.get(0), depth + 1, refStack);
    }

    String type = schema.path("type").asText("");

    // object
    if ("object".equals(type) || schema.has("properties")) {
      if (depth > 8) return new LinkedHashMap<>();
      var map = new LinkedHashMap<String, Object>();

      JsonNode props = schema.path("properties");
      if (props.isObject()) {
        props.fields().forEachRemaining(e -> {
          String name = e.getKey();
          JsonNode propSchema = e.getValue();
          map.put(name, buildValueFromOpenApi3Schema(root, propSchema, depth + 1, new HashSet<>(refStack)));
        });
      }

      // If no properties, return empty object
      return map;
    }

    // array
    if ("array".equals(type)) {
      JsonNode items = schema.path("items");
      Object itemVal = buildValueFromOpenApi3Schema(root, items, depth + 1, refStack);
      return List.of(itemVal);
    }

    // scalar
    return scalarFromSchema(schema);
  }

  /**
   * Swagger2 schema -> Java Object (Map/List/scalar)
   */
  private static Object buildValueFromSwagger2Schema(JsonNode root,
                                                     JsonNode schema,
                                                     int depth,
                                                     Set<String> refStack) {
    if (schema == null || schema.isMissingNode() || schema.isNull()) return "text";

    Object example = pickExample(schema);
    if (example != null) return example;

    // Resolve $ref "#/definitions/X"
    if (schema.has("$ref")) {
      String ref = schema.path("$ref").asText("");
      String key = ref;
      if (!refStack.add(key)) return new LinkedHashMap<>();

      String name = lastRefName(ref);
      if (!name.isEmpty()) {
        JsonNode resolved = root.path("definitions").path(name);
        Object v = buildValueFromSwagger2Schema(root, resolved, depth + 1, refStack);
        refStack.remove(key);
        return v;
      }
      refStack.remove(key);
    }

    // object
    String type = schema.path("type").asText("");
    if ("object".equals(type) || schema.has("properties")) {
      if (depth > 8) return new LinkedHashMap<>();
      var map = new LinkedHashMap<String, Object>();
      JsonNode props = schema.path("properties");
      if (props.isObject()) {
        props.fields().forEachRemaining(e -> {
          map.put(e.getKey(), buildValueFromSwagger2Schema(root, e.getValue(), depth + 1, new HashSet<>(refStack)));
        });
      }
      return map;
    }

    // array
    if ("array".equals(type)) {
      JsonNode items = schema.path("items");
      Object itemVal = buildValueFromSwagger2Schema(root, items, depth + 1, refStack);
      return List.of(itemVal);
    }

    return scalarFromSchema(schema);
  }

  private static Object scalarFromSchema(JsonNode schema) {
    // enum wins
    JsonNode en = schema.path("enum");
    if (en.isArray() && en.size() > 0) {
      return jsonNodeToJava(en.get(0));
    }

    // default wins
    JsonNode def = schema.path("default");
    if (!def.isMissingNode() && !def.isNull()) {
      return jsonNodeToJava(def);
    }

    String type = schema.path("type").asText("");
    String format = schema.path("format").asText("");

    // OpenAPI: sometimes "integer" or "number"
    if ("integer".equals(type)) return 5;
    if ("number".equals(type)) return 1.5;

    if ("boolean".equals(type)) return true;

    // date/time/email/uuid -> make plausible string
    if ("string".equals(type)) {
      if ("date-time".equals(format)) return "2026-01-31T12:00:00Z";
      if ("date".equals(format)) return "2026-01-31";
      if ("uuid".equals(format)) return "11111111-1111-1111-1111-111111111111";
      if ("email".equals(format)) return "user@example.com";
      if ("uri".equals(format) || "url".equals(format)) return "https:/example.com/";
      if ("password".equals(format)) return "Passw0rd!";
      return "text";
    }

    // fallback
    return "text";
  }

  private static Object scalarFromParam(JsonNode param) {
    // For Swagger2 formData parameter
    Object example = pickExample(param);
    if (example != null) return example;

    JsonNode en = param.path("enum");
    if (en.isArray() && en.size() > 0) return jsonNodeToJava(en.get(0));

    JsonNode def = param.path("default");
    if (!def.isMissingNode() && !def.isNull()) return jsonNodeToJava(def);

    String type = param.path("type").asText("");
    String format = param.path("format").asText("");

    if ("integer".equals(type) || "int32".equals(format) || "int64".equals(format)) return 5;
    if ("number".equals(type) || "float".equals(format) || "double".equals(format)) return 1.5;
    if ("boolean".equals(type)) return true;

    if ("string".equals(type)) {
      if ("date-time".equals(format)) return "2026-01-31T12:00:00Z";
      if ("date".equals(format)) return "2026-01-31";
      if ("uuid".equals(format)) return "11111111-1111-1111-1111-111111111111";
      if ("email".equals(format)) return "user@example.com";
      return "text";
    }

    return "text";
  }

  private static Object pickExample(JsonNode schemaOrParam) {
    // OpenAPI 3: "example" sometimes direct
    JsonNode ex = schemaOrParam.path("example");
    if (!ex.isMissingNode() && !ex.isNull()) return jsonNodeToJava(ex);

    // OpenAPI 3: examples might be under "examples" but we ignore to keep simple

    return null;
  }

  private static Object jsonNodeToJava(JsonNode n) {
    if (n == null || n.isNull() || n.isMissingNode()) return null;
    if (n.isTextual()) return n.asText();
    if (n.isInt() || n.isLong()) return n.asLong();
    if (n.isFloatingPointNumber() || n.isDouble() || n.isFloat() || n.isBigDecimal()) return n.asDouble();
    if (n.isBoolean()) return n.asBoolean();
    if (n.isArray()) {
      List<Object> list = new ArrayList<>();
      for (JsonNode x : n) list.add(jsonNodeToJava(x));
      return list;
    }
    if (n.isObject()) {
      Map<String, Object> map = new LinkedHashMap<>();
      n.fields().forEachRemaining(e -> map.put(e.getKey(), jsonNodeToJava(e.getValue())));
      return map;
    }
    return n.asText();
  }

  private static String serializeBody(String contentType, Object bodyObj) {
    if (bodyObj == null) return null;

    // For JSON content types: produce JSON
    if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
      try {
        return MAPPER.writeValueAsString(bodyObj);
      } catch (Exception ignored) {
        return "{\"value\":\"text\"}";
      }
    }

    // For x-www-form-urlencoded we already build string separately
    if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("x-www-form-urlencoded")) {
      if (bodyObj instanceof String s) return s;
      if (bodyObj instanceof Map<?, ?> m) return formUrlEncodeUnsafe(m);
    }

    // Otherwise fallback to JSON string (better than empty)
    try {
      return MAPPER.writeValueAsString(bodyObj);
    } catch (Exception ignored) {
      return String.valueOf(bodyObj);
    }
  }

  private static String formUrlEncode(Map<String, Object> form) {
    // minimal encoder (no URLEncoder to keep deps minimal)
    StringBuilder sb = new StringBuilder();
    for (var e : form.entrySet()) {
      if (sb.length() > 0) sb.append("&");
      sb.append(e.getKey()).append("=").append(String.valueOf(e.getValue()));
    }
    return sb.toString();
  }

  private static String formUrlEncodeUnsafe(Map<?, ?> form) {
    StringBuilder sb = new StringBuilder();
    for (var e : form.entrySet()) {
      if (sb.length() > 0) sb.append("&");
      sb.append(String.valueOf(e.getKey())).append("=").append(String.valueOf(e.getValue()));
    }
    return sb.toString();
  }

  private static String lastRefName(String ref) {
    if (ref == null) return "";
    String[] parts = ref.split("/");
    return parts.length > 0 ? parts[parts.length - 1] : "";
  }

  private static String pickConsumes(JsonNode op, JsonNode root) {
    JsonNode consumes = op.path("consumes");
    if (!consumes.isArray() || consumes.size() == 0) consumes = root.path("consumes");
    if (consumes.isArray() && consumes.size() > 0) {
      for (JsonNode c : consumes) {
        if ("application/json".equalsIgnoreCase(c.asText(""))) return "application/json";
      }
      return consumes.get(0).asText(null);
    }
    return null;
  }

  // ================= Result record =================

  public record ParseResult(String baseUrl, List<EndpointRow> endpoints) {}
}
