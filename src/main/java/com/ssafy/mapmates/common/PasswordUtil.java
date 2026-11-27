package com.ssafy.mapmates.common;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2 기반 비밀번호 해시. 저장 형식: salt:hash (Base64) */
public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 65536;

    private PasswordUtil() {}

    public static String hash(String raw) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(raw, salt));
    }

    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null || !stored.contains(":")) return false;
        String[] p = stored.split(":");
        byte[] salt = Base64.getDecoder().decode(p[0]);
        return MessageDigest.isEqual(pbkdf2(raw, salt), Base64.getDecoder().decode(p[1]));
    }

    public static String randomPassword() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    private static byte[] pbkdf2(String raw, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
