package com.mapmates.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 본인에게 보여주는 회원 정보. 비밀번호 해시는 절대 포함하지 않는다. */
public record MemberResponse(
        long id,
        String email,
        String name,
        String nickname,
        LocalDate birthDate,
        String gender,
        String photo,
        String bio,
        String role,
        LocalDateTime createdAt) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getEmail(), m.getName(), m.getNickname(), m.getBirthDate(),
                m.getGender(), m.getPhoto(), m.getBio(), m.getRole(), m.getCreatedAt());
    }

    /** 다른 회원에게 공개하는 정보 (이메일·이름·생년월일 제외). */
    public record Public(long id, String nickname, String gender, String photo, String bio) {
        public static Public from(Member m) {
            return new Public(m.getId(), m.getNickname(), m.getGender(), m.getPhoto(), m.getBio());
        }
    }
}
