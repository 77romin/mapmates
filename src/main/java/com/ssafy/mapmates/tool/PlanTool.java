package com.ssafy.mapmates.tool;

import java.util.List;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.dto.Plan;
import com.ssafy.mapmates.service.PlanService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 내 여행 계획 조회 Tool (내부 데이터). 사용자 식별값은 LLM 이 아닌 서버가 ToolContext 로 넣어준다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanTool {
    public static final String MEMBER_ID = "memberId";
    private final PlanService planService;

    public record PlanSummary(int id, String title, String startDate, String endDate, int budget, int placeCount) {}

    @Tool(description = "현재 로그인한 사용자가 저장한 여행 계획 목록(제목, 기간, 예산, 방문지 수)을 조회한다.")
    public List<PlanSummary> getMyPlans(ToolContext toolContext) {
        Object memberId = toolContext == null ? null : toolContext.getContext().get(MEMBER_ID);
        log.debug("[Tool] getMyPlans memberId={}", memberId);
        return memberId == null ? List.of() : myPlans((Integer) memberId);
    }

    public List<PlanSummary> myPlans(Integer memberId) {
        if (memberId == null || memberId <= 0) return List.of();
        return planService.mine(memberId).stream().map(this::toSummary).toList();
    }

    private PlanSummary toSummary(Plan p) {
        return new PlanSummary(p.getId(), p.getTitle(), p.getStartDate(), p.getEndDate(),
                p.getBudget() == null ? 0 : p.getBudget(), p.getItemCount() == null ? 0 : p.getItemCount());
    }
}
