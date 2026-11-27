package com.ssafy.mapmates.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.common.PasswordUtil;
import com.ssafy.mapmates.dao.BoardDao;
import com.ssafy.mapmates.dao.MemberDao;
import com.ssafy.mapmates.dto.Board;
import com.ssafy.mapmates.dto.Member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 회원이 한 명도 없을 때 시연용 더미 데이터(관리자·메이트 계정, 공지)를 만든다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    private final MemberDao memberDao;
    private final BoardDao boardDao;

    @Override
    public void run(String... args) {
        if (memberDao.count() > 0) return;
        Member admin = member("admin", "admin1234", "관리자", "admin@mapmates.kr", "ADMIN", "맛집,도시", 37.5013, 127.0396);
        member("mate1", "mate1234", "김메이트", "mate1@mapmates.kr", "USER", "바다,맛집,사진", 37.5665, 126.9780);
        member("mate2", "mate1234", "이동행", "mate2@mapmates.kr", "USER", "등산,맛집,역사", 37.2636, 127.0286);

        Board notice = new Board();
        notice.setType("NOTICE");
        notice.setMemberId(admin.getId());
        notice.setTitle("너랑 갈.지도 오픈 안내");
        notice.setContent("너랑 갈 다음 여행을 지도 위에서 계획해 보세요.\n지도에서 관광지를 찾아 플랜에 담고, 메이트를 모집할 수 있습니다.");
        boardDao.insert(notice);
        log.info("더미 데이터 생성 완료 (admin / mate1 / mate2)");
    }

    private Member member(String userId, String pw, String name, String email, String role, String tags, double lat, double lng) {
        Member m = new Member();
        m.setUserId(userId);
        m.setPassword(PasswordUtil.hash(pw));
        m.setName(name);
        m.setEmail(email);
        m.setRole(role);
        m.setTags(tags);
        m.setHomeLat(lat);
        m.setHomeLng(lng);
        memberDao.insert(m);
        return m;
    }
}
