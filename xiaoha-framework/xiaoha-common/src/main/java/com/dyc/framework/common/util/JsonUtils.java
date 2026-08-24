package com.dyc.framework.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsonUtils {

    private static ObjectMapper objectMapper = createDefaultObjectMapper();

    private JsonUtils() {
    }

    public static void init(ObjectMapper mapper) {
        objectMapper = mapper;
    }

    public static String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("对象转 JSON 失败", e);
        }
    }

    public static <T> T parseObject(String jsonStr, Class<T> clazz) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonStr, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转对象失败", e);
        }
    }

    public static <K, V> Map<K, V> parseMap(String jsonStr, Class<K> keyClass, Class<V> valueClass) {
        try {
            return objectMapper.readValue(jsonStr,
                    objectMapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转 Map 失败", e);
        }
    }

    public static <T> List<T> parseList(String jsonStr, Class<T> clazz) {
        try {
            return objectMapper.readValue(jsonStr,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转 List 失败", e);
        }
    }

    public static <T> Set<T> parseSet(String jsonStr, Class<T> clazz) {
        try {
            return objectMapper.readValue(jsonStr, new TypeReference<>() {
                @Override
                public com.fasterxml.jackson.databind.type.CollectionType getType() {
                    return objectMapper.getTypeFactory().constructCollectionType(Set.class, clazz);
                }
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 转 Set 失败", e);
        }
    }

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
