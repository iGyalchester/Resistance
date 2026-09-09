package com.resistance.mvc.assistant;

import java.util.List;
import java.util.Map;

/**
 * A tool the model may call, described the way the Messages API wants it:
 * a name, a sentence on when to use it, and a JSON-schema properties map.
 */
public record ToolSpec(String name, String description,
                       Map<String, Object> properties, List<String> required) {
}
