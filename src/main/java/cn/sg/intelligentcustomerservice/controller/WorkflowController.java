package cn.sg.intelligentcustomerservice.controller;

import cn.sg.intelligentcustomerservice.model.R;
import cn.sg.intelligentcustomerservice.model.WorkflowRunRequest;
import cn.sg.intelligentcustomerservice.service.WorkflowApplicationService;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowApplicationService workflowApplicationService;

    /**
     * 非流式接口：全量返回
     */
    @PostMapping("/run")
    public R<String> run(@RequestBody WorkflowRunRequest request) {
        log.info("WorkflowController[]run 接收到工作流执行请求: {}", JSONObject.toJSONString(request));
        return R.success(workflowApplicationService.run(request));
    }

    /**
     * 流式 SSE 接口：逐字推送
     */
    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> runStream(@RequestBody WorkflowRunRequest request) {
        log.info("WorkflowController[]runStream 接收到流式工作流请求: {}", JSONObject.toJSONString(request));
        return workflowApplicationService.runStream(request)
                .map(chunk -> ServerSentEvent.builder(chunk).build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").build());
    }
}
