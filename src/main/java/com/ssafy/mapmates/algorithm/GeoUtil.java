package com.ssafy.mapmates.algorithm;

/** 위경도 거리 계산 유틸. */
public final class GeoUtil {
    private static final double R = 6371.0; // 지구 반지름(km)

    private GeoUtil() {}

    /** Haversine 공식으로 두 지점 사이의 거리(km)를 구한다. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    /** 반경 km 를 위도 차이(도)로 환산 */
    public static double latDelta(double km) {
        return km / 111.0;
    }

    /** 반경 km 를 해당 위도에서의 경도 차이(도)로 환산 */
    public static double lngDelta(double km, double lat) {
        return km / (111.0 * Math.cos(Math.toRadians(lat)));
    }
}
