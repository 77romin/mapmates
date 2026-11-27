package com.ssafy.mapmates.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.mapmates.algorithm.Recommender;
import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.common.LoginUser;
import com.ssafy.mapmates.dao.MateDao;
import com.ssafy.mapmates.dao.MemberDao;
import com.ssafy.mapmates.dao.PlanDao;
import com.ssafy.mapmates.dto.MateApplication;
import com.ssafy.mapmates.dto.MatePost;
import com.ssafy.mapmates.dto.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MateService {
    private final MateDao mateDao;
    private final MemberDao memberDao;
    private final PlanDao planDao;
    private final PlanService planService;

    /** 모집글 목록. 로그인 상태면 내 취향 태그와의 코사인 유사도로 일치도를 계산해 높은 순으로 정렬한다. */
    public List<MatePost> list(LoginUser user) {
        List<MatePost> posts = mateDao.findAll();
        if (user == null) return posts;
        Member me = memberDao.findById(user.id());
        Set<String> myTags = Recommender.parseTags(me == null ? null : me.getTags());
        for (MatePost p : posts) {
            p.setMatchRate((int) Math.round(Recommender.cosine(myTags, Recommender.parseTags(p.getTags())) * 100));
            p.setMyStatus(mateDao.findMyStatus(p.getId(), user.id()));
        }
        posts.sort(Comparator.comparing((MatePost p) -> !"OPEN".equals(p.getStatus()))
                .thenComparing(MatePost::getMatchRate, Comparator.reverseOrder()));
        return posts;
    }

    public Map<String, Object> detail(int id, LoginUser user) {
        MatePost post = find(id);
        Map<String, Object> result = new LinkedHashMap<>();
        if (user != null) {
            Member me = memberDao.findById(user.id());
            post.setMatchRate((int) Math.round(Recommender.cosine(Recommender.parseTags(me.getTags()), Recommender.parseTags(post.getTags())) * 100));
            post.setMyStatus(mateDao.findMyStatus(id, user.id()));
        }
        result.put("post", post);
        result.put("items", planDao.findItems(post.getPlanId()));
        result.put("members", planDao.findMembers(post.getPlanId()));
        // 신청 목록은 모집자에게만 보여준다
        if (user != null && post.getMemberId() == user.id()) result.put("applications", mateDao.findApplications(id));
        return result;
    }

    public MatePost create(MatePost post, LoginUser user) {
        if (post.getPlanId() == null || post.getTitle() == null || post.getTitle().isBlank()) {
            throw ApiException.badRequest("여행 계획과 제목은 필수입니다.");
        }
        planService.requireMember(post.getPlanId(), user);
        post.setMemberId(user.id());
        mateDao.insert(post);
        return find(post.getId());
    }

    public void delete(int id, LoginUser user) {
        MatePost post = find(id);
        if (post.getMemberId() != user.id() && !user.isAdmin()) throw ApiException.forbidden("작성자만 삭제할 수 있습니다.");
        mateDao.delete(id);
    }

    public void apply(int postId, String message, LoginUser user) {
        MatePost post = find(postId);
        if (!"OPEN".equals(post.getStatus())) throw ApiException.badRequest("모집이 마감되었습니다.");
        if (post.getMemberId() == user.id()) throw ApiException.badRequest("본인 모집글에는 신청할 수 없습니다.");
        if (mateDao.findMyStatus(postId, user.id()) != null) throw ApiException.badRequest("이미 신청했습니다.");
        MateApplication app = new MateApplication();
        app.setPostId(postId);
        app.setMemberId(user.id());
        app.setMessage(message);
        mateDao.apply(app);
    }

    /** 수락하면 신청자를 계획 참여자(MATE)로 추가하고, 정원이 차면 모집을 마감한다. */
    @Transactional
    public void decide(int applicationId, boolean accept, LoginUser user) {
        MateApplication app = mateDao.findApplication(applicationId);
        if (app == null) throw ApiException.notFound("신청을 찾을 수 없습니다.");
        MatePost post = find(app.getPostId());
        if (post.getMemberId() != user.id()) throw ApiException.forbidden("모집자만 처리할 수 있습니다.");
        if (!"PENDING".equals(app.getStatus())) throw ApiException.badRequest("이미 처리된 신청입니다.");
        if (accept) {
            if (post.getJoinedCount() >= post.getCapacity()) throw ApiException.badRequest("정원이 가득 찼습니다.");
            planDao.addMember(post.getPlanId(), app.getMemberId(), "MATE");
            if (post.getJoinedCount() + 1 >= post.getCapacity()) mateDao.updateStatus(post.getId(), "CLOSED");
        }
        mateDao.updateApplication(applicationId, accept ? "ACCEPTED" : "REJECTED");
    }

    private MatePost find(int id) {
        MatePost post = mateDao.findById(id);
        if (post == null) throw ApiException.notFound("모집글을 찾을 수 없습니다.");
        return post;
    }
}
