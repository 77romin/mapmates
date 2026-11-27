package com.ssafy.mapmates.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Spring AI 설정 (F1201, F1202)
 * - Client : ChatClient (LLM 호출 창구). 특정 벤더 클래스가 아닌 ChatModel 추상화에만 의존한다.
 * - Prompt : resources/prompts 의 시스템 프롬프트를 기본 맥락으로 등록한다.
 * - Tool   : tool 패키지의 @Tool 빈을 요청 단위로 전달한다. (AiService, TripPlanner 참고)
 */
@Configuration
public class AiConfig {

    @Value("classpath:/prompts/system.txt")
    private Resource systemPrompt;

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).defaultSystem(systemPrompt).build();
    }
}
