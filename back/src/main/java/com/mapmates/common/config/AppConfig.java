package com.mapmates.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * classpath의 application.properties를 읽는다.
 * 같은 키의 환경변수(MAPMATES_ + 대문자, '.'과 '-'는 '_')가 있으면 환경변수가 우선한다.
 */
public final class AppConfig {

    private static final Properties PROPS = load();

    private AppConfig() {
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("application.properties를 읽지 못했습니다.", e);
        }
        return props;
    }

    public static String get(String key) {
        String envKey = "MAPMATES_" + key.toUpperCase().replace('.', '_').replace('-', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return PROPS.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("설정값 '" + key + "'이(가) 없습니다. "
                    + "src/main/resources/application.properties.example을 application.properties로 복사해 값을 채워주세요.");
        }
        return value;
    }
}
