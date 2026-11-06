package com.mapmates.member.dto;

/** 프로필 수정. 이메일·생년월일은 바꿀 수 없다(프론트 마이페이지와 동일). photo가 빈 문자열이면 사진 삭제. */
public record MemberUpdateRequest(String name, String nickname, String gender, String photo, String bio) {
}
