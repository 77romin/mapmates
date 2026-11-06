package com.mapmates.member.service;

import com.mapmates.common.db.Tx;
import com.mapmates.common.exception.ApiException;
import com.mapmates.common.security.PasswordHasher;
import com.mapmates.common.web.Validator;
import com.mapmates.member.dao.MemberDao;
import com.mapmates.member.dao.MemberDaoImpl;
import com.mapmates.member.dto.LoginRequest;
import com.mapmates.member.dto.Member;
import com.mapmates.member.dto.MemberResponse;
import com.mapmates.member.dto.MemberUpdateRequest;
import com.mapmates.member.dto.PasswordChangeRequest;
import com.mapmates.member.dto.PasswordResetRequest;
import com.mapmates.member.dto.SignupRequest;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.Period;

public class MemberServiceImpl implements MemberService {

    private static final MemberService INSTANCE = new MemberServiceImpl(MemberDaoImpl.getInstance());

    /** 프론트 account.js의 MIN_SIGNUP_AGE와 같다. */
    private static final int MIN_SIGNUP_AGE = 14;
    /** 320px JPEG data URL은 보통 수십 KB. 넉넉히 1MB까지만 허용한다. */
    private static final int MAX_PHOTO_LENGTH = 1_000_000;
    /** 존재하지 않는 이메일로 로그인할 때도 해시 계산 시간을 들여 계정 존재 여부가 드러나지 않게 한다. */
    private static final String DUMMY_HASH = PasswordHasher.hash("timing-equalizer-0");

    private final MemberDao memberDao;

    MemberServiceImpl(MemberDao memberDao) {
        this.memberDao = memberDao;
    }

    public static MemberService getInstance() {
        return INSTANCE;
    }

    @Override
    public MemberResponse signup(SignupRequest req) {
        Member m = new Member();
        m.setEmail(Validator.requireEmail(req.email()));
        Validator.requirePassword(req.password());
        m.setName(Validator.requireText(req.name(), 30, "이름"));
        m.setNickname(Validator.requireText(req.nickname(), 20, "닉네임"));
        m.setBirthDate(requireBirthDate(req.birthDate()));
        m.setGender(Validator.requireOneOf(req.gender(), "성별", "male", "female"));
        m.setPhoto(normalizePhoto(req.photo()));
        m.setBio(Validator.optionalText(req.bio(), 200, "소개"));
        m.setPasswordHash(PasswordHasher.hash(req.password()));

        return Tx.write(conn -> {
            if (memberDao.existsByEmail(conn, m.getEmail())) {
                throw ApiException.conflict("이미 가입된 이메일이에요. 로그인해주세요.");
            }
            if (memberDao.existsByNickname(conn, m.getNickname(), null)) {
                throw ApiException.conflict("이미 사용 중인 닉네임이에요.");
            }
            long id;
            try {
                id = memberDao.insert(conn, m);
            } catch (SQLIntegrityConstraintViolationException e) {
                // 동시에 같은 이메일·닉네임으로 가입한 경우 UNIQUE 제약이 막아준다.
                throw ApiException.conflict("이미 사용 중인 이메일 또는 닉네임이에요.");
            }
            return MemberResponse.from(memberDao.findById(conn, id).orElseThrow());
        });
    }

    @Override
    public MemberResponse login(LoginRequest req) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        String password = req.password() == null ? "" : req.password();
        Member member = Tx.read(conn -> memberDao.findByEmail(conn, email)).orElse(null);
        String hash = member == null ? DUMMY_HASH : member.getPasswordHash();
        if (!PasswordHasher.matches(password, hash) || member == null) {
            throw ApiException.unauthorized("이메일 또는 비밀번호를 확인해주세요.");
        }
        return MemberResponse.from(member);
    }

    @Override
    public MemberResponse getMe(long memberId) {
        return MemberResponse.from(findOrThrow(memberId));
    }

    @Override
    public MemberResponse.Public getPublicProfile(long memberId) {
        return MemberResponse.Public.from(findOrThrow(memberId));
    }

    @Override
    public MemberResponse updateProfile(long memberId, MemberUpdateRequest req) {
        String name = Validator.requireText(req.name(), 30, "이름");
        String nickname = Validator.requireText(req.nickname(), 20, "닉네임");
        String gender = Validator.requireOneOf(req.gender(), "성별", "male", "female");
        String photo = normalizePhoto(req.photo());
        String bio = Validator.optionalText(req.bio(), 200, "소개");

        return Tx.write(conn -> {
            Member m = memberDao.findById(conn, memberId)
                    .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요."));
            if (memberDao.existsByNickname(conn, nickname, memberId)) {
                throw ApiException.conflict("이미 사용 중인 닉네임이에요.");
            }
            m.setName(name);
            m.setNickname(nickname);
            m.setGender(gender);
            m.setPhoto(photo);
            m.setBio(bio);
            try {
                memberDao.updateProfile(conn, m);
            } catch (SQLIntegrityConstraintViolationException e) {
                throw ApiException.conflict("이미 사용 중인 닉네임이에요.");
            }
            return MemberResponse.from(memberDao.findById(conn, memberId).orElseThrow());
        });
    }

    @Override
    public void changePassword(long memberId, PasswordChangeRequest req) {
        Validator.requirePassword(req.newPassword());
        Member m = findOrThrow(memberId);
        if (!PasswordHasher.matches(req.currentPassword(), m.getPasswordHash())) {
            throw ApiException.badRequest("현재 비밀번호가 일치하지 않아요.");
        }
        String hash = PasswordHasher.hash(req.newPassword());
        Tx.write(conn -> memberDao.updatePassword(conn, memberId, hash));
    }

    @Override
    public void resetPassword(PasswordResetRequest req) {
        String email = Validator.requireEmail(req.email());
        String name = Validator.requireText(req.name(), 30, "이름");
        if (req.birthDate() == null) {
            throw ApiException.badRequest("생년월일을 입력해주세요.");
        }
        Validator.requirePassword(req.newPassword());
        Member m = Tx.read(conn -> memberDao.findByEmail(conn, email)).orElse(null);
        if (m == null || !m.getName().equals(name) || !m.getBirthDate().equals(req.birthDate())) {
            throw ApiException.badRequest("입력한 회원정보를 확인해주세요.");
        }
        String hash = PasswordHasher.hash(req.newPassword());
        Tx.write(conn -> memberDao.updatePassword(conn, m.getId(), hash));
    }

    @Override
    public void withdraw(long memberId, String currentPassword) {
        Member m = findOrThrow(memberId);
        if (!PasswordHasher.matches(currentPassword, m.getPasswordHash())) {
            throw ApiException.badRequest("비밀번호가 일치하지 않아요.");
        }
        // 작성한 글·댓글·계획 등은 FK ON DELETE CASCADE로 함께 정리된다(DB PJT 스키마에서 설정).
        Tx.write(conn -> memberDao.delete(conn, memberId));
    }

    @Override
    public boolean isEmailAvailable(String email) {
        String normalized = Validator.requireEmail(email);
        return !Tx.read(conn -> memberDao.existsByEmail(conn, normalized));
    }

    @Override
    public boolean isNicknameAvailable(String nickname, Long excludeMemberId) {
        String normalized = Validator.requireText(nickname, 20, "닉네임");
        return !Tx.read(conn -> memberDao.existsByNickname(conn, normalized, excludeMemberId));
    }

    // ---------- 내부 도우미 ----------

    private Member findOrThrow(long memberId) {
        return Tx.read(conn -> memberDao.findById(conn, memberId))
                .orElseThrow(() -> ApiException.notFound("회원 정보를 찾을 수 없어요."));
    }

    private static LocalDate requireBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw ApiException.badRequest("생년월일을 입력해주세요.");
        }
        LocalDate today = LocalDate.now();
        if (birthDate.isAfter(today) || birthDate.isBefore(today.minusYears(120))) {
            throw ApiException.badRequest("생년월일을 다시 확인해주세요.");
        }
        if (Period.between(birthDate, today).getYears() < MIN_SIGNUP_AGE) {
            throw ApiException.badRequest("만 " + MIN_SIGNUP_AGE + "세 이상부터 가입할 수 있어요.");
        }
        return birthDate;
    }

    /** 허용: 빈 값(사진 없음), 이미지 data URL, 프론트 기본 프로필(./assets/...), https URL. */
    private static String normalizePhoto(String photo) {
        if (photo == null || photo.isBlank()) {
            return null;
        }
        if (photo.length() > MAX_PHOTO_LENGTH) {
            throw ApiException.badRequest("프로필 사진 용량이 너무 커요.");
        }
        if (photo.startsWith("data:image/") || photo.startsWith("./assets/") || photo.startsWith("https://")) {
            return photo;
        }
        throw ApiException.badRequest("지원하지 않는 프로필 사진 형식이에요.");
    }
}
