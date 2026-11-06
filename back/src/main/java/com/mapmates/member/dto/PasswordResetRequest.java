package com.mapmates.member.dto;

import java.time.LocalDate;

/** 비밀번호 찾기: 이메일·이름·생년월일이 모두 일치하면 새 비밀번호로 바꾼다. */
public record PasswordResetRequest(String email, String name, LocalDate birthDate, String newPassword) {
}
