package com.ssafy.mapmates.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 일정 밀도(여유 / 보통 / 알차게)에 따라 하루에 넣는 장소 수를 확인한다. */
class ExecutionPlanTest {

    private static ExecutionPlan withPace(String pace) {
        return new ExecutionPlan(List.of(), "서울", "", "", 0, "", List.of(), 1, "", pace, "", "", List.of());
    }

    @Test
    @DisplayName("하루 볼거리 수는 여유 3곳, 보통 5곳, 알차게 6곳이다")
    void sightsPerDayFollowsPace() {
        assertEquals(3, withPace("여유").sightsPerDay());
        assertEquals(5, withPace("보통").sightsPerDay());
        assertEquals(6, withPace("알차게").sightsPerDay());
    }

    @Test
    @DisplayName("밀도를 말하지 않았거나 모르는 값이면 보통(5곳)으로 본다")
    void unknownPaceFallsBackToNormal() {
        assertEquals(5, withPace(null).sightsPerDay());
        assertEquals(5, withPace("").sightsPerDay());
        assertEquals(5, withPace("빡빡하게").sightsPerDay());
    }

    @Test
    @DisplayName("하루 식사는 점심과 저녁 두 번이다")
    void twoMealsPerDay() {
        assertEquals(2, ExecutionPlan.MEALS_PER_DAY);
    }
}
