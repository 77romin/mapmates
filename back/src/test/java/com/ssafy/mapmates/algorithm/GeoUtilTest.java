package com.ssafy.mapmates.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 코스의 거리 규칙(하루 이동 상한, 가장 가까운 음식점)이 기대는 거리 계산을 확인한다. */
class GeoUtilTest {

    @Test
    @DisplayName("같은 지점 사이의 거리는 0이다")
    void samePointIsZero() {
        assertEquals(0.0, GeoUtil.distanceKm(37.5759, 126.9769, 37.5759, 126.9769), 1e-9);
    }

    @Test
    @DisplayName("위도 1도 차이는 약 111km다")
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111.19, GeoUtil.distanceKm(37.0, 127.0, 38.0, 127.0), 0.05);
    }

    @Test
    @DisplayName("경복궁에서 서울역까지는 직선으로 약 2.4km다")
    void gyeongbokgungToSeoulStation() {
        assertEquals(2.4, GeoUtil.distanceKm(37.5759, 126.9769, 37.5547, 126.9707), 0.2);
    }

    @Test
    @DisplayName("km 를 위도·경도 차이로 바꾼 뒤 다시 거리를 재면 원래 km 와 거의 같다")
    void kmToDegreesRoundTrip() {
        double lat = 37.5759;
        double lng = 126.9769;

        assertEquals(10.0, GeoUtil.distanceKm(lat, lng, lat + GeoUtil.latDelta(10), lng), 0.1);
        assertEquals(10.0, GeoUtil.distanceKm(lat, lng, lat, lng + GeoUtil.lngDelta(10, lat)), 0.1);
    }
}
