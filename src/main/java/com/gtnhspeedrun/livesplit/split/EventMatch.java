package com.gtnhspeedrun.livesplit.split;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * One audit log event that a split waits for: an event type, and the values that some fields of the event must have.
 * A number matches a number of the same value, and a string matches only a string.
 *
 * <pre>
 *     {"name": "Moon", "event": "dim_first_visit", "match": {"dim": 28}}
 * </pre>
 */
public record EventMatch(String name, String eventType, Map<String, JsonPrimitive> fields) {

    public EventMatch {
        fields = Map.copyOf(fields);
    }

    public boolean matches(String type, JsonObject data) {
        if (!eventType.equals(type)) {
            return false;
        }
        for (Map.Entry<String, JsonPrimitive> field : fields.entrySet()) {
            final JsonElement value = data.get(field.getKey());
            if (!field.getValue()
                .equals(value)) {
                return false;
            }
        }
        return true;
    }
}
