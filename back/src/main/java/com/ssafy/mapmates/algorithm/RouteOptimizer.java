package com.ssafy.mapmates.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 알고리즘 #1. 여행 동선 최적화 (외판원 경로 문제, 시작점 고정·복귀 없음)
 * <ul>
 * <li>n ≤ 12 : 비트마스크 DP(Held-Karp) 로 최적해. 시간 O(n²·2ⁿ), 공간 O(n·2ⁿ)</li>
 * <li>n &gt; 12 : 최근접 이웃(Nearest Neighbor) + 2-opt 개선. 시간 O(n²) × 반복</li>
 * </ul>
 */
public final class RouteOptimizer {
    public static final int EXACT_LIMIT = 12;

    private RouteOptimizer() {}

    public record Result(List<Integer> order, double beforeKm, double afterKm, String method) {}

    /** points[i] = {lat, lng}. 0번 지점을 출발지로 고정하고 방문 순서(인덱스 목록)를 반환한다. */
    public static Result optimize(double[][] points) {
        int n = points.length;
        List<Integer> identity = new ArrayList<>();
        for (int i = 0; i < n; i++) identity.add(i);
        if (n <= 2) return new Result(identity, lengthByPoints(points, identity), lengthByPoints(points, identity), "NONE");

        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                d[i][j] = GeoUtil.distanceKm(points[i][0], points[i][1], points[j][0], points[j][1]);

        List<Integer> order;
        String method;
        if (n <= EXACT_LIMIT) {
            order = heldKarp(d);
            method = "HELD_KARP_DP";
        } else {
            order = twoOpt(nearestNeighbor(d), d);
            method = "NEAREST_NEIGHBOR_2OPT";
        }
        return new Result(order, length(d, identity), length(d, order), method);
    }

    /** dp[mask][i] = 0번에서 출발해 mask 의 지점들을 방문하고 i 에서 끝나는 최소 거리 */
    static List<Integer> heldKarp(double[][] d) {
        int n = d.length;
        int full = 1 << n;
        double[][] dp = new double[full][n];
        int[][] prev = new int[full][n];
        for (double[] row : dp) Arrays.fill(row, Double.MAX_VALUE);
        dp[1][0] = 0;
        for (int mask = 1; mask < full; mask++) {
            if ((mask & 1) == 0) continue; // 출발지는 항상 포함
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0 || dp[mask][last] == Double.MAX_VALUE) continue;
                for (int next = 1; next < n; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    int nm = mask | (1 << next);
                    double cand = dp[mask][last] + d[last][next];
                    if (cand < dp[nm][next]) {
                        dp[nm][next] = cand;
                        prev[nm][next] = last;
                    }
                }
            }
        }
        int end = 0;
        for (int i = 1; i < n; i++) if (dp[full - 1][i] < dp[full - 1][end] || end == 0) end = i;
        Integer[] path = new Integer[n];
        int mask = full - 1, cur = end;
        for (int k = n - 1; k >= 0; k--) {
            path[k] = cur;
            int p = prev[mask][cur];
            mask ^= (1 << cur);
            cur = p;
        }
        return new ArrayList<>(Arrays.asList(path));
    }

    static List<Integer> nearestNeighbor(double[][] d) {
        int n = d.length;
        boolean[] visited = new boolean[n];
        List<Integer> order = new ArrayList<>();
        int cur = 0;
        visited[0] = true;
        order.add(0);
        for (int step = 1; step < n; step++) {
            int best = -1;
            for (int j = 0; j < n; j++)
                if (!visited[j] && (best == -1 || d[cur][j] < d[cur][best])) best = j;
            visited[best] = true;
            order.add(best);
            cur = best;
        }
        return order;
    }

    /** 경로의 두 간선을 교차 해소(구간 뒤집기)하며 더 짧아지지 않을 때까지 반복한다. */
    static List<Integer> twoOpt(List<Integer> order, double[][] d) {
        int n = order.size();
        Integer[] r = order.toArray(new Integer[0]);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                for (int k = i + 1; k < n; k++) {
                    double before = d[r[i - 1]][r[i]] + (k + 1 < n ? d[r[k]][r[k + 1]] : 0);
                    double after = d[r[i - 1]][r[k]] + (k + 1 < n ? d[r[i]][r[k + 1]] : 0);
                    if (after + 1e-9 < before) {
                        for (int a = i, b = k; a < b; a++, b--) {
                            Integer t = r[a];
                            r[a] = r[b];
                            r[b] = t;
                        }
                        improved = true;
                    }
                }
            }
        }
        return new ArrayList<>(Arrays.asList(r));
    }

    private static double length(double[][] d, List<Integer> order) {
        double sum = 0;
        for (int i = 0; i + 1 < order.size(); i++) sum += d[order.get(i)][order.get(i + 1)];
        return sum;
    }

    private static double lengthByPoints(double[][] p, List<Integer> order) {
        double sum = 0;
        for (int i = 0; i + 1 < order.size(); i++) {
            double[] a = p[order.get(i)], b = p[order.get(i + 1)];
            sum += GeoUtil.distanceKm(a[0], a[1], b[0], b[1]);
        }
        return sum;
    }
}
