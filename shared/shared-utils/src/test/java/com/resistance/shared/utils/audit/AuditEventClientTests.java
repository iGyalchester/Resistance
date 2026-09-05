package com.resistance.shared.utils.audit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditEventClientTests {

    @Test
    void disabledClientEmitsNothingAndNeverThrows() {
        AuditEventClient client = AuditEventClient.disabled();
        assertFalse(client.isEnabled());
        assertDoesNotThrow(() -> client.emit("AUTH_EVENT", "LOGIN_SUCCESS", "u", "r", "1.2.3.4"));
    }

    @Test
    void unreachableEndpointNeverThrows() {
        AuditEventClient client = new AuditEventClient("http://localhost:1", "t", "resistance");
        assertDoesNotThrow(() -> client.emit("AUTH_EVENT", "LOGIN_FAILURE", "u", null, null));
    }

    @Test
    void postsJsonWithTokenHeaderToTheIngestionPath() throws Exception {
        CompletableFuture<String> body = new CompletableFuture<>();
        CompletableFuture<String> tokenHeader = new CompletableFuture<>();
        CompletableFuture<String> path = new CompletableFuture<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            path.complete(exchange.getRequestURI().getPath());
            tokenHeader.complete(exchange.getRequestHeaders().getFirst("X-Audit-Token"));
            try (InputStream in = exchange.getRequestBody()) {
                body.complete(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });
        server.start();
        try {
            AuditEventClient client = new AuditEventClient(
                    "http://localhost:" + server.getAddress().getPort(), "s3cret", "resistance");
            client.emit("AUTH_EVENT", "LOGIN_SUCCESS", "boris@gmail.com", "login", "203.0.113.7");

            String json = body.get(5, TimeUnit.SECONDS);
            assertEquals("/api/v1/events", path.get(1, TimeUnit.SECONDS));
            assertEquals("s3cret", tokenHeader.get(1, TimeUnit.SECONDS));
            assertTrue(json.contains("\"customerId\":\"resistance\""));
            assertTrue(json.contains("\"type\":\"AUTH_EVENT\""));
            assertTrue(json.contains("\"action\":\"LOGIN_SUCCESS\""));
            assertTrue(json.contains("\"userId\":\"boris@gmail.com\""));
            assertTrue(json.contains("\"eventId\":\""));
            // The source's clock, read on the calling thread before the
            // async send. AuditFlow used to stamp arrival time instead, so
            // a slow or retried delivery moved the event - a login failure
            // at 09:59 could land in the 10:00 report window. ISO-8601,
            // which is what Instant.toString() gives and what the
            // ingestion side parses back.
            assertTrue(json.contains("\"occurredAt\":\""),
                    "expected an occurredAt field in " + json);
            String occurredAt = json.replaceFirst(
                    ".*\"occurredAt\":\"([^\"]+)\".*", "$1");
            assertTrue(Math.abs(Duration.between(
                            Instant.parse(occurredAt), Instant.now()).toSeconds()) < 30,
                    "occurredAt should be about now, was " + occurredAt);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void jsonIsEscapedAndNullFieldsAreOmitted() {
        String json = AuditEventClient.toJson(new java.util.LinkedHashMap<>(Map.of(
                "a", "He said \"hi\"\nback\\slash")));
        assertEquals("{\"a\":\"He said \\\"hi\\\"\\nback\\\\slash\"}", json);

        java.util.LinkedHashMap<String, String> withNull = new java.util.LinkedHashMap<>();
        withNull.put("present", "x");
        withNull.put("absent", null);
        assertEquals("{\"present\":\"x\"}", AuditEventClient.toJson(withNull));
    }

    @Test
    void emitReturnsQuicklyEvenWhenTheServerHangs() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
            }
        });
        server.start();
        try {
            AuditEventClient client = new AuditEventClient(
                    "http://localhost:" + server.getAddress().getPort(), "", "resistance");
            long start = System.nanoTime();
            client.emit("AUTH_EVENT", "OTP_REQUESTED", "u", null, null);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            // async fire-and-forget: the caller is not held for the request
            assertTrue(elapsed.toMillis() < 1000, "emit blocked for " + elapsed.toMillis() + "ms");
        } finally {
            server.stop(0);
        }
    }
}
