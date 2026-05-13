package cn.sg.intelligentcustomerservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * LLM 配置 —— 基于 spring-ai-openai（兼容 OpenAI、DeepSeek、Qwen、DashScope 等 API）
 * <p>
 * Chat 使用 DeepSeek，Embedding 使用 DashScope 通义嵌入模型
 *
 * @author thread
 */
@Slf4j
@Configuration
public class LLMConfig {

    // ======================== Chat (DeepSeek) ========================

    @Value("${spring.ai.openai.base-url}")
    private String chatBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String chatApiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String chatModel;

    @Value("${spring.ai.openai.completions-path:v1/chat/completions}")
    private String completionsPath;

    @Bean
    public OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(chatBaseUrl)
                .apiKey(chatApiKey)
                .completionsPath(completionsPath)
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModel)
                        .temperature(0.0)
                        .build())
                .build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    // ======================== Embedding (DashScope Tongyi) ========================

    @Value("${spring.ai.embedding.openai.base-url}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.embedding.openai.api-key}")
    private String embeddingApiKey;

    @Value("${spring.ai.embedding.openai.options.model}")
    private String embeddingModel;

    @Bean
    public OpenAiApi embeddingOpenAiApi() {
        return OpenAiApi.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .embeddingsPath("/embeddings")
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenAiApi embeddingOpenAiApi) {
        return new OpenAiEmbeddingModel(embeddingOpenAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(embeddingModel)
                        .build());
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("order-auto-");
        return scheduler;
    }
}
