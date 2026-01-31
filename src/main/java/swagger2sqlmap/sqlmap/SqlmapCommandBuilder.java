package swagger2sqlmap.sqlmap;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.*;
import java.util.regex.Pattern;

public final class SqlmapCommandBuilder {

  private SqlmapCommandBuilder() {}

  public static SqlmapCommand build(HttpRequest req, Options opt) {
    Objects.requireNonNull(req, "req");
    if (opt == null) opt = Options.defaults();

    String method = safe(req.method()).toUpperCase(Locale.ROOT);
    String url = safe(req.url());

    List<String> args = new ArrayList<>();

    // executable
    args.add(opt.sqlmapExecutable());

    // url
    args.add("-u");
    args.add(url);

    // method
    if (!method.isEmpty() && !method.equals("GET")) {
      args.add("--method");
      args.add(method);
    }

    // headers: include only if user wants
    if (opt.includeHeaders()) {
      // Add Authorization + Content-Type + any extra headers (excluding Host, Content-Length, etc.)
      var headers = req.headers();
      for (var h : headers) {
        String name = safe(h.name());
        String value = safe(h.value());
        if (name.isEmpty()) continue;

        if (shouldSkipHeader(name)) continue;

        // Optionally filter only important ones
        if (opt.headersMode() == Options.HeadersMode.IMPORTANT_ONLY) {
          if (!isImportantHeader(name)) continue;
        }

        args.add("-H");
        args.add(name + ": " + value);
      }
    }

    // body
    String body = safe(req.bodyToString());
    boolean hasBody = !body.isBlank() && allowsBody(method);

    if (hasBody) {
      args.add("--data");
      args.add(body);
    }

    // baseline switches
    if (opt.batch()) args.add("--batch");
    if (opt.randomAgent()) args.add("--random-agent");

    if (opt.level() != null) {
      args.add("--level");
      args.add(String.valueOf(opt.level()));
    }
    if (opt.risk() != null) {
      args.add("--risk");
      args.add(String.valueOf(opt.risk()));
    }
    if (opt.threads() != null) {
      args.add("--threads");
      args.add(String.valueOf(opt.threads()));
    }

    // ✅ force SSL
    if (opt.forceSsl()) {
      args.add("--force-ssl");
    }

    // tamper
    if (opt.tamper() != null && !opt.tamper().isBlank()) {
      args.add("--tamper");
      args.add(opt.tamper().trim());
    }

    // technique
    if (opt.technique() != null && !opt.technique().isBlank()) {
      args.add("--technique");
      args.add(opt.technique().trim());
    }

    // user extra args
    if (opt.extraArgs() != null && !opt.extraArgs().isBlank()) {
      // split by spaces respecting simple quotes? keep simple for now
      args.addAll(splitExtraArgs(opt.extraArgs()));
    }

    return new SqlmapCommand(args);
  }

  public static String toShellCommand(SqlmapCommand cmd) {
    // produce a single-line command safe-ish for shell by quoting args
    StringBuilder sb = new StringBuilder();
    for (String a : cmd.args()) {
      if (sb.length() > 0) sb.append(" ");
      sb.append(shellQuote(a));
    }
    return sb.toString();
  }

  private static String shellQuote(String s) {
    if (s == null) return "''";
    // If safe characters only, no quotes
    if (Pattern.matches("[a-zA-Z0-9_./:@%+=,\\-]+", s)) return s;
    // single-quote strategy: 'abc'"'"'def'
    return "'" + s.replace("'", "'\"'\"'") + "'";
  }

  private static boolean allowsBody(String method) {
    return switch (method) {
      case "POST", "PUT", "PATCH", "DELETE" -> true;
      default -> false;
    };
  }

  private static boolean shouldSkipHeader(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return n.equals("host")
        || n.equals("content-length")
        || n.equals("connection")
        || n.equals("accept-encoding")
        || n.equals("user-agent")
        || n.equals("proxy-connection");
  }

  private static boolean isImportantHeader(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return n.equals("authorization")
        || n.equals("cookie")
        || n.equals("content-type")
        || n.equals("x-csrf-token")
        || n.startsWith("x-");
  }

  @SuppressWarnings("unused")
  private static String headerValue(HttpRequest req, String headerName) {
    for (var h : req.headers()) {
      if (headerName.equalsIgnoreCase(h.name())) return safe(h.value()).trim();
    }
    return "";
  }

  private static List<String> splitExtraArgs(String s) {
    // minimal split: spaces not inside quotes
    List<String> out = new ArrayList<>();
    if (s == null) return out;
    String str = s.trim();
    if (str.isEmpty()) return out;

    StringBuilder cur = new StringBuilder();
    boolean inS = false, inD = false;

    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);

      if (ch == '\'' && !inD) { inS = !inS; continue; }
      if (ch == '"' && !inS) { inD = !inD; continue; }

      if (Character.isWhitespace(ch) && !inS && !inD) {
        if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
      } else {
        cur.append(ch);
      }
    }
    if (cur.length() > 0) out.add(cur.toString());
    return out;
  }

  private static String safe(String s) { return s == null ? "" : s; }

  // ================= models =================

  public record SqlmapCommand(List<String> args) {}

  public record Options(
      String sqlmapExecutable,
      boolean includeHeaders,
      HeadersMode headersMode,
      boolean batch,
      boolean randomAgent,
      Integer level,
      Integer risk,
      Integer threads,
      String tamper,
      String technique,
      String extraArgs,
      boolean forceSsl               // ✅ NEW FIELD
  ) {
    public enum HeadersMode { IMPORTANT_ONLY, ALL }

    public static Options defaults() {
      return new Options(
          "sqlmap",
          true,
          HeadersMode.IMPORTANT_ONLY,
          true,
          true,
          3,
          2,
          null,
          null,
          null,
          "",
          false
      );
    }
  }
}
