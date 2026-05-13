package cn.sg.intelligentcustomerservice.config;

import cn.sg.intelligentcustomerservice.order.OrderDO;
import cn.sg.intelligentcustomerservice.order.OrderService;
import cn.sg.intelligentcustomerservice.service.DocumentLoaderService;
import cn.sg.intelligentcustomerservice.tool.CoffeeOrderTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 咖啡智能客服工作流
 * <p>
 * 工作流流程（以服务编排替代原先的 Alibaba StateGraph）：
 * START → intent_classification
 * ├── coffee_product → rag_retrieval → response_generation
 * ├── order         → order_processing → response_generation
 * ├── casual        → casual_chat → response_generation
 * └── non_coffee    → refuse → response_generation
 * ─────────────────────────────────────────→ response output
 */
@Slf4j
@Service
public class WorkflowConfig {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final CoffeeOrderTools orderTools;
    private final DocumentLoaderService documentLoaderService;
    private final OrderService orderService;

    @Value("${app.rag.top-k:3}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    public WorkflowConfig(ChatClient chatClient, VectorStore vectorStore, CoffeeOrderTools orderTools,
                          DocumentLoaderService documentLoaderService, OrderService orderService) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.orderTools = orderTools;
        this.documentLoaderService = documentLoaderService;
        this.orderService = orderService;
    }

    /**
     * 执行工作流入口
     *
     * @param inputParam 输入参数，必须包含 userInput，可选 history/user
     * @return 最终回复文本
     */
    public String execute(Map<String, Object> inputParam) {
        String userInput = (String) inputParam.getOrDefault("userInput", "");
        String history = (String) inputParam.getOrDefault("history", "");
        String userInfo = (String) inputParam.getOrDefault("user", "");

        // 1. 意图分类
        String intent = classifyIntent(userInput, history);

        // 2. 根据意图路由处理
        return switch (intent) {
            case "coffee_product" -> handleCoffeeProduct(userInput);
            case "order" -> handleOrder(userInput, userInfo, history);
            case "casual" -> handleCasual(userInput, history);
            case "non_coffee" -> refuseAnswer(userInput);
            default -> {
                log.warn("未识别的意图: {}, 兜底处理", intent);
                yield "您好，我是三更咖啡厅的智能助手，请问有什么可以帮您的吗？☕";
            }
        };
    }

    // ========================================================================
    //  意图分类
    // ========================================================================
    private String classifyIntent(String userInput, String history) {
        if (userInput.isBlank()) {
            return "casual";
        }

        String prompt = """
                你是一个咖啡客服系统的意图分类器。请判断用户输入属于以下哪个类别：
                
                1. coffee_product - 用户询问咖啡产品知识（种类、口感、价格、推荐、区别等）
                2. order - 用户询问订单相关（下单、查订单、取消订单、完成订单等）
                3. casual - 用户进行与咖啡相关的闲聊（打招呼、感谢、日常对话，如"你好"、"谢谢"、"今天天气真好"）
                4. non_coffee - 与咖啡完全无关的内容（如询问编程、数学、其他行业知识等）
                
                只需要返回类别名称，不要返回其他内容。
                
                用户输入：%s
                """.formatted(userInput);

        String intent = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        if (intent == null || intent.isBlank()) {
            return "casual";
        }

        intent = intent.trim().toLowerCase();
        if (intent.contains("coffee_product")) {
            return "coffee_product";
        }
        if (intent.contains("order")) {
            return "order";
        }
        if (intent.contains("casual")) {
            return "casual";
        }
        if (intent.contains("non_coffee")) {
            return "non_coffee";
        }

        log.warn("意图分类结果异常: {}, 兜底为 casual", intent);
        return "casual";
    }

    // ========================================================================
    //  咖啡产品知识问答
    // ========================================================================
    private String handleCoffeeProduct(String userInput) {
        log.info("RAG 检索: {}", userInput);

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userInput)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build()
        );

        String knowledgeContext;
        if (docs.isEmpty()) {
            // RAG 未命中，尝试使用 CSV 内存知识库兜底
            String memoryKnowledge = documentLoaderService.getKnowledgeBaseText();
            if (!memoryKnowledge.isBlank()) {
                log.info("RAG 未命中，使用内存知识库兜底");
                knowledgeContext = memoryKnowledge;
            } else {
                // 实在没有知识库，由 LLM 凭自身知识回答
                log.warn("RAG 与内存知识库均为空，依赖 LLM 自身知识");
                knowledgeContext = chatClient.prompt()
                        .user("""
                                你是一个咖啡知识库，请回答以下咖啡相关问题。\
                                如果问题不在咖啡知识范围内，请回答"我不知道"。\
                                如果能在咖啡知识范围内回答，请提供详细且准确的回答。
                                
                                问题：%s
                                """.formatted(userInput))
                        .call()
                        .content();
            }
        } else {
            knowledgeContext = docs.stream()
                    .map(doc -> {
                        String question = String.valueOf(doc.getMetadata().getOrDefault("question", ""));
                        String answer = String.valueOf(doc.getMetadata().getOrDefault("answer", ""));
                        return "【知识】问题：" + question + " → 回答：" + answer;
                    })
                    .collect(Collectors.joining("\n"));
            log.info("RAG 检索到 {} 条知识", docs.size());
        }

        log.debug("handleCoffeeProduct - 实际知识库内容:\n{}", knowledgeContext);

        // 把检索到的知识组织成自然语言回答
        return chatClient.prompt()
                .system("""
                        你是三更咖啡厅的智能客服助手。
                        """)
                .user("""
                        用户的问题是：%s
                        
                        【以下是你需要使用的咖啡知识，请直接用这些知识回答用户，不要说你没有知识库】
                        %s
                        
                        请基于以上知识回答用户的问题。如果这些知识足以回答，直接用它们回复。
                        如果以上知识中缺少用户问的具体信息，就告诉用户目前可选的咖啡有哪些。
                        """.formatted(userInput, knowledgeContext))
                .call()
                .content();
    }

    // ========================================================================
    //  订单处理
    // ========================================================================
    private String handleOrder(String userInput, String userInfo, String history) {
        log.info("订单处理: {}", userInput);

        // 方案：先让 LLM 解析意图和参数，再由代码直接调用订单服务
        // 避免 DeepSeek 推理模式 + Spring AI 工具调用导致的 reasoning_content 兼容问题
        String analysisPrompt = """
                分析用户的订单请求，提取意图和信息。只输出 JSON，不要包含其他内容。
                
                支持的意图：
                - create：下单/创建订单
                - query_all：查询所有订单
                - query_user：查询我的订单
                - detail：查询订单详情
                - complete：完成订单
                - cancel：取消/退款订单
                - ask_info：信息不足，需要向用户询问
                
                如果意图是 create，请同时提取：userId（用下面已知用户的ID）、userName、userPhone、itemName（商品名称）、price（价格）
                
                咖啡价格参考：美式9.9元，拿铁8.8元，其他咖啡19.9元。如果用户没给价格但说了咖啡名，直接使用参考价格。
                
                注意：以下是当前登录用户的已知信息，如果用户没提供姓名/手机号/ID，就使用下面的信息：
                %s
                
                输出格式：
                {"intent": "...", "reason": "...", 其他提取的字段...}
                
                用户输入：%s
                
                对话历史：
                %s
                """.formatted(userInfo, userInput, history);

        String analysisJson = chatClient.prompt()
                .user(analysisPrompt)
                .call()
                .content();

        log.debug("订单分析结果: {}", analysisJson);

        // 解析 JSON（简化解析，不依赖 JSON 库）
        String intent = extractJsonValue(analysisJson, "intent");
        log.debug("识别到的订单意图: {}", intent);

        return switch (intent != null ? intent : "") {
            case "create" -> handleCreateOrder(analysisJson, userInput);
            case "detail" -> {
                String orderNo = extractJsonValue(analysisJson, "orderNumber");
                yield orderService.orderDetail(orderNo != null ? orderNo : "");
            }
            case "query_all" -> {
                List<OrderDO> orders = orderService.orders();
                if (orders.isEmpty()) yield "目前没有订单。";
                yield "当前共有 " + orders.size() + " 笔订单：\n\n" +
                        orders.stream().map(OrderDO::toStr).collect(Collectors.joining("\n---\n"));
            }
            case "query_user" -> {
                String uid = extractJsonValue(analysisJson, "userId");
                List<OrderDO> orders = orderService.userOrder(uid != null ? uid : "");
                if (orders.isEmpty()) yield "您还没有订单。";
                yield "您的订单：\n\n" +
                        orders.stream().map(OrderDO::toStr).collect(Collectors.joining("\n---\n"));
            }
            case "complete" -> {
                String orderNo = extractJsonValue(analysisJson, "orderNumber");
                orderService.complete(orderNo != null ? orderNo : "");
                yield "订单 " + orderNo + " 已完成 ✅";
            }
            case "cancel" -> {
                String orderNo = extractJsonValue(analysisJson, "orderNumber");
                yield orderService.cancel(orderNo != null ? orderNo : "");
            }
            case "ask_info" -> {
                String reason = extractJsonValue(analysisJson, "reason");
                yield reason != null ? reason : "请问您的姓名和手机号是多少？想点什么咖啡？价格是多少？";
            }
            default -> {
                log.warn("未识别的订单意图: {}", intent);
                yield chatClient.prompt()
                        .user("你是一个咖啡客服助手。用户说：" + userInput + "。请用友好的语气回复，告诉用户可以帮你：下单、查订单、完成订单、取消订单。")
                        .call()
                        .content();
            }
        };
    }

    private String handleCreateOrder(String analysisJson, String userInput) {
        String userName = extractJsonValue(analysisJson, "userName");
        String userPhone = extractJsonValue(analysisJson, "userPhone");
        String itemName = extractJsonValue(analysisJson, "itemName");
        String price = extractJsonValue(analysisJson, "price");
        String userId = extractJsonValue(analysisJson, "userId");

        // 从知识库查找价格（无需用户输入）
        if ((price == null || price.isBlank()) && itemName != null) {
            price = lookupPriceFromKnowledge(itemName);
        }

        // 信息不完整，通过 LLM 生成提问（附知识库价格供参考）
        if (userName == null || userPhone == null || itemName == null || price == null) {
            String priceHint = documentLoaderService.getKnowledgeBaseText();
            if (priceHint.length() > 200) priceHint = priceHint.substring(0, 200) + "...";
            String askPrompt = """
                    用户想下单，但信息不完整。请用友好的语气询问缺少的信息。
                    已有的信息：姓名=%s, 手机=%s, 商品=%s, 价格=%s
                    用户原话：%s
                    
                    以下咖啡价格供你参考（如果用户没给价格，你可以直接使用参考价格）：
                    %s
                    """.formatted(
                    userName != null ? userName : "未知",
                    userPhone != null ? userPhone : "未知",
                    itemName != null ? itemName : "未知",
                    price != null ? price : "未知",
                    userInput,
                    priceHint);
            return chatClient.prompt().user(askPrompt).call().content();
        }

        // 信息完整，直接下单
        if (userId == null) userId = "guest";
        String result = orderService.createOrder(userId, userName, userPhone, price, itemName);
        log.info("订单创建成功: {}", result);

        String replyPrompt = """
                用户下单成功，请用友好的语气告诉用户。
                订单信息：%s
                """.formatted(result);
        return chatClient.prompt().user(replyPrompt).call().content();
    }

    /** 从知识库模糊查找咖啡价格 */
    private String lookupPriceFromKnowledge(String itemName) {
        String knowledge = documentLoaderService.getKnowledgeBaseText();
        if (knowledge.isBlank()) return null;
        // 简单匹配：查找包含咖啡名称的行
        for (String line : knowledge.split("\n\n")) {
            if (line.contains(itemName)) {
                // 提取价格信息
                int priceIdx = line.indexOf("价格");
                if (priceIdx >= 0) {
                    return line.substring(priceIdx);
                }
            }
        }
        // 兜底：返回价格知识全文
        if (knowledge.contains("价格")) {
            for (String line : knowledge.split("\n\n")) {
                if (line.contains("价格")) return line;
            }
        }
        return null;
    }

    /**
     * 简易 JSON 字段提取（不依赖 JSON 库，避免多一层依赖）
     */
    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        // 跳过空格
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        // 处理字符串值
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf('"', start);
            return end > start ? json.substring(start, end) : null;
        }
        // 处理数字/布尔等
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
        String val = json.substring(start, end).trim();
        return val.isEmpty() ? null : val;
    }

    // ========================================================================
    //  闲聊
    // ========================================================================
    private String handleCasual(String userInput, String history) {
        log.info("闲聊处理: {}", userInput);

        String systemPrompt = """
                你是三更咖啡厅的智能客服助手，名字叫"小咖"。
                你热情友好、活泼亲切，但始终围绕咖啡话题进行交流。
                
                你可以和用户进行友好的日常对话，如打招呼、问候、感谢等。
                请保持回答简短、温暖、有趣，最好能回归到咖啡相关的话题上。
                
                对话历史：
                %s
                """.formatted(history);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userInput)
                .call()
                .content();
    }

    // ========================================================================
    //  拒绝回答非咖啡相关问题
    // ========================================================================
    private String refuseAnswer(String userInput) {
        log.info("拒绝回答: {}", userInput);

        return """
                抱歉，我是三更咖啡厅的智能客服助手，只回答与咖啡产品、订单相关的问题。
                如果您有咖啡方面的疑问或需要查询订单，请随时告诉我！☕
                """;
    }

    // ========================================================================
    //  上下文压缩
    // ========================================================================

    /**
     * 流式执行工作流
     * 编排逻辑与 execute() 相同，仅最终响应改为逐块推送
     */
    public Flux<String> executeStream(Map<String, Object> inputParam) {
        String userInput = (String) inputParam.getOrDefault("userInput", "");
        String history = (String) inputParam.getOrDefault("history", "");
        String userInfo = (String) inputParam.getOrDefault("user", "");

        // 1. 意图分类（阻塞）
        String intent = classifyIntent(userInput, history);

        // 2. 根据意图执行（阻塞获取结果）
        String result = switch (intent) {
            case "coffee_product" -> handleCoffeeProduct(userInput);
            case "order" -> handleOrder(userInput, userInfo, history);
            case "casual" -> handleCasual(userInput, history);
            case "non_coffee" -> refuseAnswer(userInput);
            default -> {
                log.warn("未识别的意图: {}, 兜底处理", intent);
                yield "您好，我是三更咖啡厅的智能助手，请问有什么可以帮您的吗？☕";
            }
        };

        // 3. 分块流式推送（每块约 5 个字符）
        int chunkSize = 5;
        java.util.List<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < result.length(); i += chunkSize) {
            chunks.add(result.substring(i, Math.min(i + chunkSize, result.length())));
        }
        return Flux.fromIterable(chunks);
    }

    /**
     * 将一段较长的对话历史压缩为简短摘要
     * 供 MessageDomainService 在上下文压缩时调用
     */
    public String compressConversation(String conversation) {
        if (conversation == null || conversation.isBlank()) {
            return "";
        }

        String prompt = """
                请将以下对话压缩为一段简洁的摘要，保留关键信息：
                - 用户的核心需求 / 意图
                - AI 的关键回复 / 结论
                - 已完成的动作（如下单、查订单等）
                
                用中文一句话概括，不超过 200 字。
                
                对话内容：
                %s
                """.formatted(conversation);

        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("对话压缩失败", e);
            return "";
        }
    }
}
