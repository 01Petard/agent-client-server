package cn.sg.intelligentcustomerservice.service;

import cn.sg.intelligentcustomerservice.config.WorkflowConfig;
import cn.sg.intelligentcustomerservice.model.User;
import cn.sg.intelligentcustomerservice.model.WorkflowRunRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 工作流应用服务
 * 负责调用 WorkflowConfig 工作流处理用户请求
 *
 * @author thread
 */
@Slf4j
@Service
@AllArgsConstructor
public class WorkflowApplicationService {
    private final WorkflowConfig workflowConfig;
    private final MessageDomainService messageDomainService;
    private final UserDomainService userDomainService;

    public String run(WorkflowRunRequest request) {
        log.info("工作流执行 - userId: {}, input: {}", request.userId(), request.userInput());

        try {
            // 0. 解析用户ID
            Long userIdLong = parseUserId(request.userId());

            // 1. 获取用户信息和上下文（压缩后的历史）
            User user = userIdLong != null ? userDomainService.get(userIdLong) : null;
            String userInfo = user != null ? user.toStr() : "游客用户";
            String history = messageDomainService.compressedHistory(request.userId(), workflowConfig);

            // 2. 保存用户消息
            messageDomainService.addUserMsg(request.userId(), request.userInput());

            // 3. 构建输入参数
            Map<String, Object> inputParam = new HashMap<>();
            inputParam.put("userInput", request.userInput());
            inputParam.put("userId", request.userId());
            inputParam.put("history", history);
            inputParam.put("user", userInfo);

            // 4. 执行工作流（直接调用服务编排，替代原先的 StateGraph）
            String output = Optional.ofNullable(workflowConfig.execute(inputParam))
                    .filter(s -> !s.isBlank())
                    .orElse("您好，我是三更咖啡厅的智能助手，请问有什么可以帮您的吗？☕");

            // 5. 保存助手回复
            messageDomainService.addAssistantMsg(request.userId(), output);

            log.info("工作流执行完成 - userId: {}", request.userId());
            return output;

        } catch (Exception e) {
            log.error("执行工作流失败", e);
            throw new RuntimeException("系统内部错误，请稍后再试");
        }
    }

    /**
     * 流式执行工作流
     */
    public Flux<String> runStream(WorkflowRunRequest request) {
        log.info("流式工作流执行 - userId: {}, input: {}", request.userId(), request.userInput());

        // 0. 解析用户ID
        Long userIdLong = parseUserId(request.userId());
        User user = userIdLong != null ? userDomainService.get(userIdLong) : null;
        String userInfo = user != null ? user.toStr() : "游客用户";

        try {
            String history = messageDomainService.compressedHistory(request.userId(), workflowConfig);

            // 保存用户消息
            messageDomainService.addUserMsg(request.userId(), request.userInput());

            Map<String, Object> inputParam = new HashMap<>();
            inputParam.put("userInput", request.userInput());
            inputParam.put("userId", request.userId());
            inputParam.put("history", history);
            inputParam.put("user", userInfo);

            // 收集所有块，流结束后保存完整回复
            StringBuilder fullResponse = new StringBuilder();
            return workflowConfig.executeStream(inputParam)
                    .doOnNext(fullResponse::append)
                    .doOnComplete(() -> {
                        // 保存助手回复
                        if (!fullResponse.isEmpty()) {
                            messageDomainService.addAssistantMsg(request.userId(), fullResponse.toString());
                        }
                        log.info("流式工作流执行完成 - userId: {}", request.userId());
                    });

        } catch (Exception e) {
            log.error("执行流式工作流失败", e);
            return Flux.error(new RuntimeException("系统内部错误，请稍后再试"));
        }
    }

    private Long parseUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) return null;
        try { return Long.parseLong(userIdStr); }
        catch (NumberFormatException e) { return null; }
    }
}
