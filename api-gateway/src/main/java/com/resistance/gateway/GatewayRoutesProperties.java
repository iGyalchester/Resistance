package com.resistance.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a leading path segment to a downstream base URL, e.g.
 * gateway.routes.rest-api=http://localhost:8083 makes
 * GET /rest-api/api/applications proxy to http://localhost:8083/api/applications.
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayRoutesProperties {

    private Map<String, String> routes = new LinkedHashMap<>();

    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }
}
