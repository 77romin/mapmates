package com.mapmates.member.service;

import com.mapmates.member.dto.LoginRequest;
import com.mapmates.member.dto.MemberResponse;
import com.mapmates.member.dto.MemberUpdateRequest;
import com.mapmates.member.dto.PasswordChangeRequest;
import com.mapmates.member.dto.PasswordResetRequest;
import com.mapmates.member.dto.SignupRequest;

/** 회원 관리(F107)와 로그인 관리(F108). */
public interface MemberService {

    MemberResponse signup(SignupRequest request);

    /** 이메일·비밀번호가 맞으면 회원 정보를, 아니면 401 ApiException. */
    MemberResponse login(LoginRequest request);

    MemberResponse getMe(long memberId);

    MemberResponse.Public getPublicProfile(long memberId);

    MemberResponse updateProfile(long memberId, MemberUpdateRequest request);

    void changePassword(long memberId, PasswordChangeRequest request);

    void resetPassword(PasswordResetRequest request);

    void withdraw(long memberId, String currentPassword);

    boolean isEmailAvailable(String email);

    boolean isNicknameAvailable(String nickname, Long excludeMemberId);
}
