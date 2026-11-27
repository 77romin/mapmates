package com.ssafy.mapmates.tool;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.service.InfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 날씨 조회 Tool (외부 API: 기상청 단기예보). 실제 연동은 InfoService 에 위임한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeatherTool {
    private final InfoService infoService;

    @Tool(description = "위도와 경도에 해당하는 지점의 단기 날씨 예보(기온 TMP, 강수확률 POP, 하늘상태)와 일출·일몰 시각을 조회한다.")
    public Map<String, Object> getWeather(@ToolParam(description = "위도") double latitude,
            @ToolParam(description = "경도") double longitude) {
        log.debug("[Tool] getWeather lat={}, lng={}", latitude, longitude);
        Map<String, Object> result = new LinkedHashMap<>(infoService.weather(latitude, longitude));
        result.put("sun", infoService.sun(latitude, longitude, LocalDate.now()));
        return result;
    }
}
