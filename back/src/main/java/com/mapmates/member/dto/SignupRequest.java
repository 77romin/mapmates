package com.mapmates.member.dto;

import java.time.LocalDate;

public record SignupRequest(
        String email,
        String password,
        String name,
        String nickname,
        LocalDate birthDate,
        String gender,
        String photo,
        String bio) {
}
