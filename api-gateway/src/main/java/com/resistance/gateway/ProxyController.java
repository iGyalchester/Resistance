package com.resistance.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * Forwards /{service}/** to the base URL configured for that service in
 * gateway.routes. Intentionally minimal - just enough of a gateway to put
 * one host in front of the individual services.
 */
@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final RestClient restClient;
    private final GatewayRoutesProperties routes;

    public ProxyController(RestClient.Builder restClientBuilder, GatewayRoutesProperties routes) {
        this.restClient = restClientBuilder.build();
        this.routes = routes;
    }

    @RequestMapping("/{service}/**")
    public ResponseEntity<byte[]> proxy(@PathVariable String service,
                                        @RequestBody(required = false) byte[] body,
                                        HttpServletRequest request) {

        String target = routes.getRoutes().get(service);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }

        String downstreamPath = request.getRequestURI().substring(("/" + service).length());
        String query = request.getQueryString();
        URI uri = URI.create(target + downstreamPath + (query != null ? "?" + query : ""));

        log.debug("Proxying {} {} -> {}", request.getMethod(), request.getRequestURI(), uri);

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(uri)
                .headers(headers -> copyRequestHeaders(request, headers));

        if (body != null && body.length > 0) {
            spec.body(body);
        }

        // exchange() so downstream error statuses pass through untouched
        return spec.exchange((clientRequest, clientResponse) -> {
            byte[] responseBody = clientResponse.getBody().readAllBytes();
            HttpHeaders responseHeaders = new HttpHeaders();
            MediaType contentType = clientResponse.getHeaders().getContentType();
            if (contentType != null) {
                responseHeaders.setContentType(contentType);
            }
            return ResponseEntity.status(clientResponse.getStatusCode())
                    .headers(responseHeaders)
                    .body(responseBody);
        });
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        for (String name : new String[]{HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE}) {
            String value = request.getHeader(name);
            if (value != null) {
                headers.set(name, value);
            }
        }
    }
}
