package com.ssafy.mapmates.common;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 같은 사람이 같은 글을 여러 번 열어도 조회수는 한 번만 올리기 위한 기록.
 * 로그인 사용자는 회원 번호, 비로그인은 접속 IP 로 구분하고 24시간 동안 기억한다.
 * 서버 메모리에 두므로 서버를 다시 띄우면 초기화된다. (서버가 여러 대가 되면 Redis 같은 공용 저장소로 옮겨야 한다)
 */
@Component
public class ViewDedup {
    private static final long TTL_MS = Duration.ofHours(24).toMillis();
    private static final int MAX_ENTRIES = 50_000;
    private final Map<String, Long> seen = new ConcurrentHashMap<>();

    /** 이 요청이 해당 글의 '처음 보는 조회'면 true 를 돌려주고 기록한다. */
    public boolean firstView(String kind, int id, HttpServletRequest req) {
        LoginUser user = AuthInterceptor.get(req);
        String who = user != null ? "m" + user.id() : "ip" + req.getRemoteAddr();
        String key = kind + ":" + id + ":" + who;
        long now = System.currentTimeMillis();
        if (seen.size() > MAX_ENTRIES) seen.values().removeIf(t -> now - t > TTL_MS);
        boolean[] first = { false };
        // compute 는 키 단위로 원자적이라, 동시에 두 요청이 와도 한 번만 처음으로 본다
        seen.compute(key, (k, last) -> {
            if (last == null || now - last > TTL_MS) {
                first[0] = true;
                return now;
            }
            return last;
        });
        return first[0];
    }
}
