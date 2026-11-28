package com.ssafy.mapmates.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ssafy.mapmates.algorithm.GeoUtil;
import com.ssafy.mapmates.planner.CourseBuilder.Stop;
import com.ssafy.mapmates.tool.AttractionTool.Place;

/**
 * AI 가 고른 장소를 일차별 코스로 묶는 규칙을 확인한다.
 * LLM 과 DB 없이 돌아가도록, 경복궁을 기준점으로 삼아 동쪽·북쪽으로 몇 km 떨어진 가상의 장소를 만들어 쓴다.
 */
class CourseBuilderTest {
    private static final double BASE_LAT = 37.5759;
    private static final double BASE_LNG = 126.9769;

    /** 기준점에서 동쪽으로 eastKm, 북쪽으로 northKm 떨어진 장소 */
    private static Place place(int no, String type, double eastKm, double northKm) {
        return new Place(no, type + no, type, "서울 종로구",
                BASE_LAT + GeoUtil.latDelta(northKm), BASE_LNG + GeoUtil.lngDelta(eastKm, BASE_LAT));
    }

    private static Place sight(int no, double eastKm) {
        return place(no, "관광지", eastKm, 0);
    }

    private static Place food(int no, double eastKm) {
        return place(no, "음식점", eastKm, 0);
    }

    private static List<Integer> nos(List<Stop> stops) {
        return stops.stream().map(Stop::no).toList();
    }

    private static List<String> slots(List<Stop> stops) {
        return stops.stream().map(Stop::slot).toList();
    }

    private static List<Integer> days(List<Stop> stops) {
        return stops.stream().map(Stop::dayNo).toList();
    }

    // ---------- 일차 나누기 ----------

    @Test
    @DisplayName("볼거리는 동선 순서를 유지한 채 일수만큼 고르게 나뉜다")
    void splitsSightsEvenlyAcrossDaysInRouteOrder() {
        List<Place> route = List.of(sight(1, 0), sight(2, 1), sight(3, 2), sight(4, 3), sight(5, 4), sight(6, 5));

        List<Stop> stops = CourseBuilder.assign(route, 2);

        assertEquals(List.of(1, 2, 3, 4, 5, 6), nos(stops));
        assertEquals(List.of(1, 1, 1, 2, 2, 2), days(stops));
    }

    @Test
    @DisplayName("볼거리가 일수보다 적으면 빈 날을 만들지 않고 일수를 줄인다")
    void shrinksDayCountWhenSightsAreFewerThanDays() {
        List<Stop> stops = CourseBuilder.assign(List.of(sight(1, 0), sight(2, 1)), 3);

        assertEquals(List.of(1, 2), days(stops));
    }

    @Test
    @DisplayName("일수가 0 이하로 들어와도 하루 코스로 짠다")
    void treatsNonPositiveDaysAsOneDay() {
        List<Stop> stops = CourseBuilder.assign(List.of(sight(1, 0), sight(2, 1)), 0);

        assertEquals(List.of(1, 1), days(stops));
    }

    // ---------- 하루 틀: 오전 → 점심 → 오후 → 저녁 → 숙박 ----------

    @Test
    @DisplayName("하루는 오전 볼거리, 점심, 오후 볼거리, 저녁 순서로 짜인다")
    void buildsDayAsMorningLunchAfternoonDinner() {
        List<Place> route = List.of(sight(1, 0), sight(2, 2), sight(3, 4), sight(4, 6), food(11, 3), food(12, 6.2));

        List<Stop> stops = CourseBuilder.assign(route, 1);

        assertEquals(List.of("오전", "오전", "점심", "오후", "오후", "저녁"), slots(stops));
        assertEquals(List.of(1, 2, 11, 3, 4, 12), nos(stops));
    }

    @Test
    @DisplayName("점심은 오전 마지막과 오후 첫 장소의 중간, 저녁은 마지막 볼거리에서 가장 가까운 음식점이고 남는 음식점은 넣지 않는다")
    void picksNearestRestaurantsAndDropsTheRest() {
        Place nearStart = food(10, 0.1);  // 출발지 바로 옆: 점심·저녁 어느 쪽에도 가장 가깝지 않다
        Place nearMiddle = food(11, 3);   // 2km 지점과 4km 지점의 중간
        Place nearEnd = food(12, 6.2);    // 마지막 볼거리(6km) 옆
        List<Place> route = List.of(sight(1, 0), nearStart, sight(2, 2), sight(3, 4), sight(4, 6), nearEnd, nearMiddle);

        List<Stop> stops = CourseBuilder.assign(route, 1);

        Stop lunch = stops.stream().filter(s -> "점심".equals(s.slot())).findFirst().orElseThrow();
        Stop dinner = stops.stream().filter(s -> "저녁".equals(s.slot())).findFirst().orElseThrow();
        assertEquals(11, lunch.no());
        assertEquals(12, dinner.no());
        assertFalse(nos(stops).contains(10));
    }

    @Test
    @DisplayName("음식점이 모자라면 점심부터 채우고 저녁은 비워 둔다")
    void fillsLunchBeforeDinnerWhenRestaurantsAreScarce() {
        List<Place> route = List.of(sight(1, 0), sight(2, 1), sight(3, 2), sight(4, 3), food(11, 0.5));

        List<Stop> stops = CourseBuilder.assign(route, 2);

        assertEquals(1L, stops.stream().filter(s -> "점심".equals(s.slot())).count());
        assertEquals(0L, stops.stream().filter(s -> "저녁".equals(s.slot())).count());
        assertEquals(1, stops.stream().filter(s -> "점심".equals(s.slot())).findFirst().orElseThrow().dayNo());
    }

    @Test
    @DisplayName("숙박은 마지막 날을 뺀 날의 끝에 둔다")
    void putsStayAtTheEndOfEveryDayButTheLast() {
        Place stay = place(21, "숙박", 1.5, 0);
        List<Place> route = List.of(sight(1, 0), sight(2, 1), stay, sight(3, 2), sight(4, 3));

        List<Stop> stops = CourseBuilder.assign(route, 2);

        List<Stop> day1 = stops.stream().filter(s -> s.dayNo() == 1).toList();
        List<Stop> day2 = stops.stream().filter(s -> s.dayNo() == 2).toList();
        assertEquals(21, day1.get(day1.size() - 1).no());
        assertEquals("숙박", day1.get(day1.size() - 1).slot());
        assertFalse(slots(day2).contains("숙박"));
    }

    @Test
    @DisplayName("맛집만 요청하면 음식점이 방문지가 되고 식사 자리로 나누지 않는다")
    void treatsRestaurantsAsSightsWhenOnlyFoodIsRequested() {
        List<Place> route = List.of(food(1, 0), food(2, 1), food(3, 2), food(4, 3));

        List<Stop> stops = CourseBuilder.assign(route, 2);

        assertEquals(List.of(1, 2, 3, 4), nos(stops));
        assertEquals(List.of(1, 1, 2, 2), days(stops));
        assertEquals(List.of("", "", "", ""), slots(stops));
    }

    // ---------- 하루 이동 거리 상한 ----------

    @Test
    @DisplayName("하루 이동 거리가 상한(25km) 이하면 그대로 둔다")
    void keepsDayWithinDistanceLimit() {
        List<List<Place>> byDay = new ArrayList<>();
        byDay.add(new ArrayList<>(List.of(sight(1, 0), sight(2, 10), sight(3, 20))));

        CourseBuilder.tighten(byDay);

        assertEquals(List.of(1, 2, 3), byDay.get(0).stream().map(Place::no).toList());
    }

    @Test
    @DisplayName("상한을 넘으면 가장 동떨어진 곳을 가까운(8km 안) 다른 날로, 가장 가까운 장소 바로 뒤에 옮긴다")
    void movesOutlierToNearbyDay() {
        List<List<Place>> byDay = new ArrayList<>();
        byDay.add(new ArrayList<>(List.of(sight(1, 0), sight(2, 1), sight(3, 40)))); // 3번이 40km 떨어져 있다
        byDay.add(new ArrayList<>(List.of(sight(4, 41), sight(5, 42))));             // 둘째 날은 그 근처

        CourseBuilder.tighten(byDay);

        assertEquals(List.of(1, 2), byDay.get(0).stream().map(Place::no).toList());
        assertEquals(List.of(4, 3, 5), byDay.get(1).stream().map(Place::no).toList());
    }

    @Test
    @DisplayName("옮길 만한 날이 없으면 동떨어진 곳을 코스에서 뺀다")
    void dropsOutlierWhenNoDayIsNearby() {
        List<List<Place>> byDay = new ArrayList<>();
        byDay.add(new ArrayList<>(List.of(sight(1, 0), sight(2, 1), sight(3, 40))));
        byDay.add(new ArrayList<>(List.of(place(4, "관광지", 0, 30), place(5, "관광지", 1, 30))));

        CourseBuilder.tighten(byDay);

        assertEquals(List.of(1, 2), byDay.get(0).stream().map(Place::no).toList());
        assertEquals(List.of(4, 5), byDay.get(1).stream().map(Place::no).toList());
    }

    @Test
    @DisplayName("하루에 두 곳만 남으면 멀어도 더 줄이지 않는다")
    void neverShrinksDayBelowTwoPlaces() {
        List<List<Place>> byDay = new ArrayList<>();
        byDay.add(new ArrayList<>(List.of(sight(1, 0), sight(2, 60))));

        CourseBuilder.tighten(byDay);

        assertEquals(2, byDay.get(0).size());
    }

    @Test
    @DisplayName("코스를 짤 때도 거리 상한이 적용되어, 혼자 동떨어진 볼거리는 결과에서 빠진다")
    void assignAppliesDistanceLimit() {
        List<Stop> stops = CourseBuilder.assign(List.of(sight(1, 0), sight(2, 1), sight(3, 40)), 1);

        assertEquals(List.of(1, 2), nos(stops));
        assertTrue(CourseBuilder.dayKm(stops).get(0) <= CourseBuilder.DAY_MAX_KM);
    }

    // ---------- 동선 순서, 화면·답변용 출력 ----------

    @Test
    @DisplayName("동선 최적화 결과가 있으면 그 순서를 따르고, 결과에 빠진 장소는 뒤에 붙인다")
    void ordersByOptimizedRouteAndAppendsMissing() {
        List<Place> places = List.of(sight(1, 0), sight(2, 1), sight(3, 2));

        List<Place> ordered = CourseBuilder.order(places, Map.of("order", List.of(3, 1)));

        assertEquals(List.of(3, 1, 2), ordered.stream().map(Place::no).toList());
    }

    @Test
    @DisplayName("동선 최적화 결과가 없으면 추천 순서 그대로 둔다")
    void keepsOrderWithoutRoute() {
        List<Place> places = List.of(sight(2, 1), sight(1, 0));

        assertEquals(places, CourseBuilder.order(places, "실행 실패"));
        assertEquals(places, CourseBuilder.order(places, null));
    }

    private static Stop stop(int no, String title, double eastKm, int dayNo, String slot) {
        Place p = sight(no, eastKm);
        return new Stop(no, title, "관광지", p.address(), p.latitude(), p.longitude(), dayNo, slot, null, null);
    }

    @Test
    @DisplayName("일차별 이동 거리는 같은 날 장소끼리만 더하고 소수 첫째 자리로 반올림한다")
    void sumsDistancePerDay() {
        List<Stop> stops = List.of(stop(1, "A", 0, 1, "오전"), stop(2, "B", 5, 1, "오후"), stop(3, "C", 30, 2, ""));

        assertEquals(List.of(5.0, 0.0), CourseBuilder.dayKm(stops));
    }

    @Test
    @DisplayName("답변을 쓰는 모델에게 넘기는 설명에 일차, 이동 거리, 하루 중 자리가 들어간다")
    void describesCourseForAnswerModel() {
        List<Stop> stops = List.of(stop(1, "A", 0, 1, "오전"), stop(2, "B", 5, 1, "오후"), stop(3, "C", 30, 2, ""));

        String expected = "1일차 (이동 약 5.0km):\n"
                + "  1. [오전] A (관광지)\n"
                + "  2. [오후] B (관광지)\n"
                + "2일차 (이동 약 0.0km):\n"
                + "  1. C (관광지)\n";
        assertEquals(expected, CourseBuilder.describe(stops));
    }

    @Test
    @DisplayName("코스 제목은 당일과 n박 m일을 구분하고, 지역이 없으면 지역 없이 쓴다")
    void buildsCourseTitle() {
        assertEquals("서울 당일 코스", CourseBuilder.title("서울", 1));
        assertEquals("부산 1박 2일 코스", CourseBuilder.title("부산", 2));
        assertEquals("2박 3일 코스", CourseBuilder.title("", 3));
        assertEquals("당일 코스", CourseBuilder.title(null, 0));
    }

    @Test
    @DisplayName("소개 글은 첫 문장만 쓰고, 문장 끝을 못 찾으면 70자에서 자른다")
    void takesFirstSentenceOfOverview() {
        assertEquals("경복궁은 조선의 법궁이다.", CourseBuilder.firstSentence("경복궁은 조선의 법궁이다. 1395년에 창건되었다."));

        String noEnding = "가".repeat(100);
        assertEquals("가".repeat(70) + "…", CourseBuilder.firstSentence(noEnding));
    }
}
