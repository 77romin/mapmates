package com.ssafy.mapmates.tool;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssafy.mapmates.service.DocumentSearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 문서 검색 Tool. 분리된 AI 서버(FastAPI RAG)의 벡터 검색을 DocumentSearchService 를 통해 호출한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentTool {
    private final DocumentSearchService documentSearchService;

    @Tool(description = "사용자가 업로드한 여행 문서(가이드, 팁, 규정)에서 질문과 관련된 문단을 검색한다. 각 결과에는 본문(text), 출처 파일(source), 유사도(score)가 포함된다.")
    public List<Map<String, Object>> searchDocuments(@ToolParam(description = "검색할 질문 또는 키워드") String query) {
        log.debug("[Tool] searchDocuments query={}", query);
        return documentSearchService.search(query, 3);
    }
}
