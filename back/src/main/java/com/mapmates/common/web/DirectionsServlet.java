package com.mapmates.common.web;

import com.mapmates.common.config.AppConfig;
import com.mapmates.common.exception.ApiException;
import jakarta.servlet.annotation.WebServlet;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@WebServlet("/api/kakao-directions")
public class DirectionsServlet extends BaseApiServlet {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private String point(String v) {
    if (v == null) throw ApiException.badRequest("출발지·도착지가 필요해요.");
    String[] xy = v.split(",");
    if (xy.length != 2) throw ApiException.badRequest("좌표를 확인해주세요.");
    try {
      double x = Double.parseDouble(xy[0]), y = Double.parseDouble(xy[1]);
      if (!Double.isFinite(x) || !Double.isFinite(y) || x < 124 || x > 132 || y < 33 || y > 39)
        throw new NumberFormatException();
      return x + "," + y;
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("좌표를 확인해주세요.");
    }
  }

  protected void routes(Routes r) {
    r.get(
        "/",
        q -> {
          String key = AppConfig.get("kakao.rest-key");
          if (key == null || key.isBlank())
            throw new ApiException(503, "NOT_CONFIGURED", "차량 경로 API 키가 설정되지 않았어요.");
          String query =
              "origin="
                  + URLEncoder.encode(
                      point(q.query("origin")), java.nio.charset.StandardCharsets.UTF_8)
                  + "&destination="
                  + URLEncoder.encode(
                      point(q.query("destination")), java.nio.charset.StandardCharsets.UTF_8);
          if (q.query("waypoints") != null) {
            var points = q.query("waypoints").split("\\|");
            if (points.length > 5) throw ApiException.badRequest("경유지가 너무 많아요.");
            var clean = new ArrayList<String>();
            for (var p : points) clean.add(point(p));
            query +=
                "&waypoints="
                    + URLEncoder.encode(
                        String.join("|", clean), java.nio.charset.StandardCharsets.UTF_8);
          }
          var req =
              HttpRequest.newBuilder(
                      URI.create("https://apis-navi.kakaomobility.com/v1/directions?" + query))
                  .timeout(Duration.ofSeconds(15))
                  .header("Authorization", "KakaoAK " + key)
                  .GET()
                  .build();
          var response = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
          try (var body = response.body()) {
            if (response.statusCode() != 200)
              throw new ApiException(502, "UPSTREAM_ERROR", "차량 경로 조회에 실패했어요.");
            return JsonUtil.read(body, Object.class);
          }
        });
  }
}
