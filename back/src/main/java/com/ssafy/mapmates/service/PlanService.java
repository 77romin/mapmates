package com.ssafy.mapmates.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.mapmates.algorithm.Recommender;
import com.ssafy.mapmates.algorithm.RouteOptimizer;
import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.common.LoginUser;
import com.ssafy.mapmates.dao.PlanDao;
import com.ssafy.mapmates.dto.Attraction;
import com.ssafy.mapmates.dto.Member;
import com.ssafy.mapmates.dto.Plan;
import com.ssafy.mapmates.dto.PlanItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlanService {
    private final PlanDao planDao;
    private final AttractionService attractionService;

    public List<Plan> mine(int memberId) { return planDao.findMine(memberId); }
    public List<Plan> publicPlans() { return planDao.findPublic(); }

    /** 공개 계획은 누구나, 비공개 계획은 참여자만 볼 수 있다. */
    public Plan get(int id, LoginUser user) {
        Plan plan = planDao.findById(id);
        if (plan == null) throw ApiException.notFound("여행 계획을 찾을 수 없습니다.");
        boolean member = user != null && planDao.isMember(id, user.id()) > 0;
        if (!Boolean.TRUE.equals(plan.getIsPublic()) && !member) throw ApiException.forbidden("비공개 계획입니다.");
        plan.setItems(planDao.findItems(id));
        plan.setMembers(planDao.findMembers(id));
        return plan;
    }

    @Transactional
    public Plan create(Plan plan, LoginUser user) {
        if (plan.getTitle() == null || plan.getTitle().isBlank()) throw ApiException.badRequest("계획 제목을 입력해 주세요.");
        plan.setMemberId(user.id());
        planDao.insert(plan);
        planDao.addMember(plan.getId(), user.id(), "OWNER");
        saveItems(plan.getId(), plan.getItems());
        return get(plan.getId(), user);
    }

    /** 참여자(작성자+메이트)라면 누구나 계획을 함께 편집할 수 있다. */
    @Transactional
    public Plan update(int id, Plan req, LoginUser user) {
        requireMember(id, user);
        if (req.getTitle() == null || req.getTitle().isBlank()) throw ApiException.badRequest("계획 제목을 입력해 주세요.");
        req.setId(id);
        planDao.update(req);
        planDao.deleteItems(id);
        saveItems(id, req.getItems());
        return get(id, user);
    }

    public void delete(int id, LoginUser user) {
        Plan plan = planDao.findById(id);
        if (plan == null) throw ApiException.notFound("여행 계획을 찾을 수 없습니다.");
        if (plan.getMemberId() != user.id() && !user.isAdmin()) throw ApiException.forbidden("작성자만 삭제할 수 있습니다.");
        planDao.delete(id);
    }

    /** 방문지 번호 목록의 최적 방문 순서를 계산한다. 첫 번째 방문지가 출발지로 고정된다. */
    public Map<String, Object> optimize(List<Integer> attractionNos) {
        List<Attraction> list = attractionService.findByNos(attractionNos);
        if (list.size() < 2) throw ApiException.badRequest("최적화하려면 방문지가 2곳 이상 필요합니다.");
        double[][] points = list.stream().map(a -> new double[] { a.getLatitude(), a.getLongitude() }).toArray(double[][]::new);
        RouteOptimizer.Result r = RouteOptimizer.optimize(points);
        List<Attraction> ordered = r.order().stream().map(list::get).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", r.method());
        result.put("beforeKm", Math.round(r.beforeKm() * 10) / 10.0);
        result.put("afterKm", Math.round(r.afterKm() * 10) / 10.0);
        result.put("order", ordered.stream().map(Attraction::getNo).toList());
        result.put("attractions", ordered);
        return result;
    }

    /** 메이트들의 출발지에서 이동 거리 합이 최소인 중간 집결지와 그 주변 장소 */
    public Map<String, Object> meetingPoint(int planId, LoginUser user) {
        requireMember(planId, user);
        List<double[]> homes = new ArrayList<>();
        for (Member m : planDao.findMembers(planId)) {
            if (m.getHomeLat() != null && m.getHomeLng() != null) homes.add(new double[] { m.getHomeLat(), m.getHomeLng() });
        }
        if (homes.size() < 2) throw ApiException.badRequest("출발지를 등록한 메이트가 2명 이상이어야 합니다. (마이페이지에서 출발지 설정)");
        double[] median = Recommender.geometricMedian(homes);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latitude", median[0]);
        result.put("longitude", median[1]);
        result.put("memberCount", homes.size());
        result.put("nearby", attractionService.nearby(median[0], median[1], 5, List.of(12, 14, 39), 5));
        return result;
    }

    public void requireMember(int planId, LoginUser user) {
        if (planDao.findById(planId) == null) throw ApiException.notFound("여행 계획을 찾을 수 없습니다.");
        if (planDao.isMember(planId, user.id()) == 0) throw ApiException.forbidden("계획 참여자만 가능합니다.");
    }

    private void saveItems(int planId, List<PlanItem> items) {
        if (items == null) return;
        int seq = 0;
        for (PlanItem item : items) {
            if (item.getAttractionNo() == null) continue;
            item.setPlanId(planId);
            item.setSeq(seq++);
            planDao.insertItem(item);
        }
    }
}
