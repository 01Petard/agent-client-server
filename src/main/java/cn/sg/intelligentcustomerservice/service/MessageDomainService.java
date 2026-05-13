package cn.sg.intelligentcustomerservice.service;

import cn.sg.intelligentcustomerservice.config.WorkflowConfig;
import cn.sg.intelligentcustomerservice.mapper.MessageMapper;
import cn.sg.intelligentcustomerservice.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 消息领域服务
 * 提供消息存储、历史记录、上下文压缩等能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDomainService {

    /** 保留最近消息数，之前的进行压缩 */
    private static final int RECENT_COUNT = 5;

    private final MessageMapper messageMapper;

    public void addUserMsg(String userId, String content) {
        messageMapper.insert(Message.ofUser(userId, content));
    }

    public void addAssistantMsg(String userId, String content) {
        messageMapper.insert(Message.ofAssistant(userId, content));
    }

    /**
     * 原始历史（全量，适用于首次/短对话）
     */
    public String history(String userId) {
        List<Message> messages = messageMapper.findAllByUserId(userId);
        return Optional.ofNullable(messages).orElse(Collections.emptyList()).stream()
                .map(item -> item.getRole() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 压缩后的上下文历史
     * <p>
     * 策略：
     * 1. 保留最近 RECENT_COUNT 条消息作为原始上下文
     * 2. 之前的消息调用 LLM 压缩为一段简短摘要
     * 3. 最终返回「【历史摘要】...【最近对话】...」
     * <p>
     * 这种「摘要+最近消息」的方式能大幅减少 token 消耗，
     * 同时保留完整对话脉络。
     */
    public String compressedHistory(String userId, WorkflowConfig workflowConfig) {
        List<Message> messages = messageMapper.findAllByUserId(userId);
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        // 少于 RECENT_COUNT 条，直接用原始历史
        if (messages.size() <= RECENT_COUNT) {
            return messages.stream()
                    .map(item -> item.getRole() + ": " + item.getContent())
                    .collect(Collectors.joining("\n"));
        }

        // 1. 分割：旧消息（需压缩） + 最近消息（保留原始）
        List<Message> oldMessages = messages.subList(0, messages.size() - RECENT_COUNT);
        List<Message> recentMessages = messages.subList(messages.size() - RECENT_COUNT, messages.size());

        // 2. 压缩旧消息
        String oldLines = oldMessages.stream()
                .map(item -> item.getRole() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));

        String summary = compressWithLLM(oldLines, workflowConfig);

        // 3. 最近消息保留原始格式
        String recentLines = recentMessages.stream()
                .map(item -> item.getRole() + ": " + item.getContent())
                .collect(Collectors.joining("\n"));

        String compressed = "";
        if (!summary.isEmpty()) {
            compressed += "【历史摘要】" + summary + "\n\n";
        }
        compressed += "【最近对话】\n" + recentLines;

        log.debug("最近对话: {}", recentLines);

        log.debug("历史摘要: {}", summary);

        log.debug("上下文压缩完成: 原始{}条→摘要+最近{}条", messages.size(), RECENT_COUNT);
        return compressed;
    }

    /**
     * 调用 LLM 对历史对话进行压缩摘要
     */
    private String compressWithLLM(String conversation, WorkflowConfig workflowConfig) {
        try {
            return workflowConfig.compressConversation(conversation);
        } catch (Exception e) {
            log.warn("上下文压缩失败，回退使用原始历史", e);
            // 压缩失败就返回原始历史的前 N 条，保证最少有上下文
            String[] lines = conversation.split("\n");
            int maxLines = Math.min(lines.length, 10);
            StringBuilder fallback = new StringBuilder();
            for (int i = 0; i < maxLines; i++) {
                fallback.append(lines[i]).append("\n");
            }
            return fallback.toString();
        }
    }
}
