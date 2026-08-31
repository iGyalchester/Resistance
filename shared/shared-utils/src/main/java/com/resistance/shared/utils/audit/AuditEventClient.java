package com.resistance.shared.utils.audit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fire-and-forget emitter of audit events to an AuditFlow ingestion
 * endpoint. Design constraints, in order:
 *
 * <ol>
 *   <li><b>Auditing must never break the app.</b> emit() is asynchronous,
 *       bounded by a short timeout, and swallows every failure (logged at
 *       WARNING). Delivery is therefore at-most-once by design - the
 *       tradeoff is documented, not hidden.</li>
 *   <li><b>Zero dependencies.</b> Pure JDK (java.net.http + System.Logger),
 *       so both mvc-service and intake-service can share it without
 *       dragging a framework into shared-utils.</li>
 *   <li><b>Off unless configured.</b> A blank URL yields a disabled client
 *       whose emit() is a no-op - dev works with nothing running.</li>
 * </ol>
 */
public class AuditEventClient {

    private static final System.Logger log = System.getLogger(AuditEventClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String token;
    private final String customerId;
    private final boolean enabled;

    public AuditEventClient(String baseUrl, String token, String customerId) {
        this.enabled = baseUrl != null && !baseUrl.isBlank();
        this.endpoint = enabled ? URI.create(baseUrl.replaceAll("/$", "") + "/api/v1/events") : null;
        this.token = token == null ? "" : token;
        this.customerId = customerId;
        this.httpClient = enabled
                ? HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
                : null;
    }

    /** A client whose emit() does nothing - for tests and unconfigured dev. */
    public static AuditEventClient disabled() {
        return new AuditEventClient("", "", "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param type     AuditFlow EventType name, e.g. AUTH_EVENT, DATABASE_QUERY, FILE_ACCESS
     * @param action   what happened, e.g. LOGIN_SUCCESS, CREATE, PROFILE_VIEW
     * @param userId   the acting/affected account identifier (may be null)
     * @param resource what was touched, e.g. "job_application:42" (may be null)
     * @param ipAddress caller IP when known (may be null)
     */
    public void emit(String type, String action, String userId, String resource, String ipAddress) {
        if (!enabled) {
            return;
        }
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("eventId", UUID.randomUUID().toString());
            fields.put("customerId", customerId);
            fields.put("userId", userId);
            fields.put("type", type);
            fields.put("resource", resource);
            fields.put("action", action);
            fields.put("ipAddress", ipAddress);

            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(fields)));
            if (!token.isBlank()) {
                request.header("X-Audit-Token", token);
            }

            httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            log.log(System.Logger.Level.WARNING,
                                    "Audit event not delivered: {0}", error.toString());
                        } else if (response.statusCode() >= 300) {
                            log.log(System.Logger.Level.WARNING,
                                    "Audit event rejected with status {0}", response.statusCode());
                        }
                    });
        } catch (Exception e) {
            // never let auditing take the caller down
            log.log(System.Logger.Level.WARNING, "Audit event not emitted: {0}", e.toString());
        }
    }

    static String toJson(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
