package com.payflow.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Jsons {

    private final ObjectMapper mapper;

    public Jsons(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not write JSON", ex);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read JSON", ex);
        }
    }

    public String canonical(Object body) {
        if (body == null) {
            return "";
        }
        try {
            JsonNode tree = mapper.valueToTree(body);
            return mapper.writeValueAsString(sort(tree));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not canonicalize JSON", ex);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) {
                sorted.set(name, sort(node.get(name)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            node.forEach(child -> array.add(sort(child)));
            return array;
        }
        return node;
    }
}
