package com.ssafy.mapmates.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/** 분리된 AI 서버(FastAPI + ChromaDB)의 /search API 를 호출한다. 서버가 꺼져 있으면 빈 결과를 돌려준다. */
@Service
@Slf4j
public class DocumentSearchService {
    private final RestClient restClient = RestClient.create();

    @Value("${mapmates.ai-server.url}")
    private String aiServerUrl;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String query, int topK) {
        try {
            Map<String, Object> body = restClient.post().uri(aiServerUrl + "/search")
                    .body(Map.of("query", query, "top_k", topK)).retrieve().body(Map.class);
            Object results = body == null ? null : body.get("results");
            return results instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        } catch (Exception e) {
            log.warn("AI 서버 문서 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }
}
