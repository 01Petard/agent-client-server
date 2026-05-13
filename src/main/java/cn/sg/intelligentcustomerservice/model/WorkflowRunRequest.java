package cn.sg.intelligentcustomerservice.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 工作流执行请求
 */
public record WorkflowRunRequest(
        @NotBlank(message = "userId is not blank")
        String userId,
        @NotBlank(message = "userInput is not blank")
        String userInput) {
}
