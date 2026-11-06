package com.mapmates.common.web;

import com.mapmates.common.exception.ApiException;

import java.util.regex.Pattern;

/** 공통 입력 검증. 실패하면 400 ApiException을 던진다. 문구는 프론트와 맞춘다. */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private Validator() {
    }

    public static String requireText(String value, int maxLength, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest(label + "을(를) 입력해주세요.");
        }
        if (trimmed.length() > maxLength) {
            throw ApiException.badRequest(label + "은(는) " + maxLength + "자 이하로 입력해주세요.");
        }
        return trimmed;
    }

    public static String optionalText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, maxLength, label);
    }

    public static String requireEmail(String value) {
        String email = requireText(value, 100, "이메일").toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("올바른 이메일 주소를 입력해주세요.");
        }
        return email;
    }

    /** 프론트 signup.js와 같은 규칙: 영문과 숫자를 포함해 8자 이상. */
    public static void requirePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || !LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
            throw ApiException.badRequest("비밀번호는 영문과 숫자를 포함해 8~64자로 입력해주세요.");
        }
    }

    public static String requireOneOf(String value, String label, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw ApiException.badRequest(label + "을(를) 선택해주세요.");
    }
}
