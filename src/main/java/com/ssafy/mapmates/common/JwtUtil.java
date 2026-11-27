package com.ssafy.mapmates.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** HS256 JWT 발급/검증 (외부 라이브러리 없이 구현). */
@Component
public class JwtUtil {
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern PAYLOAD = Pattern.compile("\\{\"sub\":(\\d+),\"uid\":\"([^\"]*)\",\"role\":\"([^\"]*)\",\"exp\":(\\d+)}");

    @Value("${mapmates.jwt.secret}")
    private String secret;
    @Value("${mapmates.jwt.expire-minutes}")
    private long expireMinutes;

    public String create(LoginUser user) {
        long exp = System.currentTimeMillis() / 1000 + expireMinutes * 60;
        String payload = "{\"sub\":%d,\"uid\":\"%s\",\"role\":\"%s\",\"exp\":%d}".formatted(user.id(), user.userId(), user.role(), exp);
        String body = HEADER + "." + ENC.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    /** 유효하면 사용자 정보를, 아니면 null 을 반환한다. */
    public LoginUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String body = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) return null;
            Matcher m = PAYLOAD.matcher(new String(DEC.decode(parts[1]), StandardCharsets.UTF_8));
            if (!m.matches()) return null;
            if (Long.parseLong(m.group(4)) < System.currentTimeMillis() / 1000) return null;
            return new LoginUser(Integer.parseInt(m.group(1)), m.group(2), m.group(3));
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENC.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
