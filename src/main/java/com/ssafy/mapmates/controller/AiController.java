package com.ssafy.mapmates.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.common.ApiException;
import com.ssafy.mapmates.common.AuthInterceptor;
import com.ssafy.mapmates.common.LoginUser;
import com.ssafy.mapmates.service.AiService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** AI 길잡이 API. 사용자 요청을 받아 LLM / Tool 호출 흐름을 선택한다. */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {
    private final AiService aiService;

    /** body: { message, mode: planner(기본) | multi | single | simple, tool: single 일 때 사용할 Tool } */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String message = body.get("message");
        if (message == null || message.isBlank()) throw ApiException.badRequest("질문을 입력해 주세요.");
        if (message.length() > 1000) throw ApiException.badRequest("질문은 1000자 이내로 입력해 주세요.");
        LoginUser user = AuthInterceptor.get(req);
        Integer memberId = user == null ? null : user.id();
        String mode = body.getOrDefault("mode", "planner");
        try {
            return switch (mode) {
                case "simple" -> aiService.simple(message);
                case "single" -> aiService.single(message, body.get("tool"), memberId);
                case "multi" -> aiService.multi(message, memberId);
                default -> aiService.planner(message, memberId);
            };
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 응답 생성 실패", e);
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
