package com.mapmates.common.security;

import java.io.Serializable;

/** HttpSession에 저장하는 최소한의 로그인 정보. 비밀번호·개인정보는 담지 않는다. */
public record SessionUser(long id, String email, String nickname, String role,long authVersion) implements Serializable {

    public SessionUser(long id,String email,String nickname,String role){this(id,email,nickname,role,0);}

    public static final String SESSION_KEY = "LOGIN_USER";

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
