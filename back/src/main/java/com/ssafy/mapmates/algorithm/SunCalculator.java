package com.ssafy.mapmates.algorithm;

import java.time.LocalDate;
import java.time.LocalTime;

/** 위경도와 날짜로 일출/일몰 시각(KST)을 계산한다. (NOAA 근사식) */
public final class SunCalculator {
    private SunCalculator() {}

    public record SunTimes(String sunrise, String sunset) {}

    public static SunTimes calc(double lat, double lng, LocalDate date) {
        int n = date.getDayOfYear();
        double gamma = 2 * Math.PI / 365 * (n - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double latRad = Math.toRadians(lat);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(latRad) * Math.cos(decl)) - Math.tan(latRad) * Math.tan(decl);
        cosHa = Math.max(-1, Math.min(1, cosHa));
        double ha = Math.toDegrees(Math.acos(cosHa));
        double sunriseUtc = 720 - 4 * (lng + ha) - eqTime; // 분
        double sunsetUtc = 720 - 4 * (lng - ha) - eqTime;
        return new SunTimes(format(sunriseUtc + 540), format(sunsetUtc + 540));
    }

    private static String format(double minutes) {
        int m = (int) Math.round(((minutes % 1440) + 1440) % 1440);
        return LocalTime.of(m / 60 % 24, m % 60).toString();
    }
}
