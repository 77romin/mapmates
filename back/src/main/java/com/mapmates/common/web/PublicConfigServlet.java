package com.mapmates.common.web;

import com.mapmates.common.config.AppConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

/** Only browser-scoped keys are exposed here; REST and database credentials stay on the server. */
@WebServlet("/config.js")
public class PublicConfigServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setContentType("application/javascript;charset=UTF-8");
    response.setHeader("Cache-Control", "no-store");
    var writer = response.getWriter();
    writer.write("window.APP_CONFIG = ");
    JsonUtil.write(
        writer,
        Map.of(
            "KAKAO_JS_KEY",
            AppConfig.get("kakao.js-key", ""),
            "WEATHER_API_KEY",
            AppConfig.get("weather.api-key", ""),
            "EV_CHARGER_API_KEY",
            AppConfig.get("ev-charger.api-key", "")));
  }
}
