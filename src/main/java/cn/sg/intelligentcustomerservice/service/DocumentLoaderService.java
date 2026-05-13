package cn.sg.intelligentcustomerservice.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 文档加载服务
 * 负责在应用启动时加载 CSV 知识库文档。
 * <p>
 * 双重策略：
 * 1. 优先加载到 Redis 向量存储（用于语义相似度检索 RAG）
 * 2. 始终保留一份纯文本副本在内存中（RAG 失效时的兜底知识库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLoaderService implements ApplicationRunner {
    private final VectorStore vectorStore;

    @Value("${app.knowledge.csv-path:data/qa.csv}")
    private String csvPath;

    /** RAG 兜底：纯文本知识库，vectorStore.add() 无论是否成功都会加载 */
    private final List<QAData> knowledgeBase = new ArrayList<>();

    /**
     * 获取纯文本知识库内容（RAG 兜底用）
     */
    public String getKnowledgeBaseText() {
        if (knowledgeBase.isEmpty()) {
            return "";
        }
        return knowledgeBase.stream()
                .map(qa -> "【知识】" + qa.getQuestion() + " → " + qa.getAnswer())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 应用启动后自动执行文档加载
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始异步加载知识库文档...");
        loadDocumentsAsync();
    }

    /**
     * 异步加载文档
     */
    public void loadDocumentsAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                loadDocuments();
            } catch (Exception e) {
                log.error("异步加载文档失败", e);
            }
        });
    }

    /**
     * 加载CSV文档到向量数据库 + 内存兜底
     */
    public void loadDocuments() {
        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            log.warn("CSV文件不存在: {}", csvPath);
            return;
        }
        log.info("开始从CSV文件加载文档: {}", csvPath);
        try {
            // 1. 读取 CSV（使用 @ExcelProperty 映射中文表头）
            List<QAData> allRows = EasyExcel.read(csvFile)
                    .head(QAData.class)
                    .sheet()
                    .doReadSync()
                    .stream()
                    .map(row -> (QAData) row)
                    .filter(qa -> qa.getQuestion() != null && !qa.getQuestion().isBlank()
                            && qa.getAnswer() != null)
                    .toList();

            // 2. 保存到内存兜底知识库（无论 RAG 是否成功）
            knowledgeBase.clear();
            knowledgeBase.addAll(allRows);
            log.info("知识库加载到内存完成: {} 条 QA", knowledgeBase.size());

            // 3. 尝试写入 Redis 向量存储（用于 RAG 语义检索）
            List<Document> docs = allRows.stream()
                    .map(this::toDocument)
                    .toList();
            Lists.partition(docs, 10).forEach(batch -> {
                try {
                    vectorStore.add(batch);
                } catch (Exception e) {
                    log.warn("写入向量存储失败（批次跳过），仍可使用内存兜底知识库", e);
                }
            });
            log.info("从CSV文件加载文档完成: {}", csvPath);

        } catch (Exception e) {
            log.error("加载文档过程中发生错误", e);
        }
    }

    private Document toDocument(QAData qa) {
        String id = DigestUtil.md5Hex(qa.getQuestion());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", id);
        metadata.put("question", qa.getQuestion());
        metadata.put("answer", qa.getAnswer());
        String content = qa.getQuestion() + " " + qa.getAnswer();
        return new Document(id, content, metadata);
    }

    @Data
    public static class QAData {
        @ExcelProperty("问题")
        private String question;

        @ExcelProperty("回答")
        private String answer;
    }
}
