package com.mapmates.member.dto;

/** 비밀번호 변경과 회원 탈퇴 확인에 함께 쓴다. 탈퇴 시에는 currentPassword만 사용한다. */
public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
