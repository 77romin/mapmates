package com.ssafy.mapmates.common;

/** 토큰에서 복원한 로그인 사용자. */
public record LoginUser(int id, String userId, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
