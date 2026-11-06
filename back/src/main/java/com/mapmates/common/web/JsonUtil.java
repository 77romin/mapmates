package com.mapmates.common.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/** 애플리케이션 전체가 공유하는 ObjectMapper. LocalDate는 "2026-10-12" 형식으로 주고받는다. */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonUtil() {
    }

    public static <T> T read(InputStream in, Class<T> type) throws IOException {
        return MAPPER.readValue(in, type);
    }

    public static void write(Writer writer, Object value) throws IOException {
        MAPPER.writeValue(writer, value);
    }
}
