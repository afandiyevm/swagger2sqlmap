package swagger2sqlmap.model;

public record EndpointRow(
    String method,
    String path,
    String operationId,
    String summary,
    String contentType,     
    String bodyTemplate     
) {}
