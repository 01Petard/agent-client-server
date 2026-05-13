# agent-client-server — 智能咖啡客服后端

## 项目简介

基于 Spring Boot 3 + Spring AI + MyBatis-Plus 的智能咖啡客服后端服务，提供 AI 对话、订单管理、用户管理、RAG 知识库等功能。

## 演示截图

<img src="https://cdn.jsdelivr.net/gh/01Petard/imageURL@main/img/202605131250857.png" alt="image-20260513125017690" style="zoom:50%;" />

<img src="/Users/hzx/Library/Application Support/typora-user-images/image-20260513125026778.png" alt="image-20260513125026778" style="zoom:50%;" />

## 技术栈

| 模块        | 选型                                          |
|-----------|----------------------------------------------|
| 框架        | Spring Boot 3.5.3                            |
| AI 框架     | Spring AI 1.0.1 + OpenAI 兼容接口（DeepSeek）   |
| ORM       | MyBatis-Plus 3.5.14 + P6Spy SQL 监控          |
| 数据库      | MySQL 8.x                                    |
| 多数据源     | dynamic-datasource-spring-boot3-starter 4.5.0 |
| 向量存储     | Redis（RAG 知识库语义检索）                        |
| 嵌入模型     | DashScope text-embedding-v3（通义千问）           |
| 嵌入部署模型   | spring-ai-transformers                       |
| RAG       | CSV 知识库 → Redis Vector Store               |
| 流式输出     | SSE (Server-Sent Events)                     |
| 构建工具     | Maven                                        |
| JDK       | 17+                                          |

## 工作流说明

客服工作流采用服务编排模式（替代 Alibaba StateGraph）：

```
用户输入 → 意图分类(LLM)
  ├── coffee_product → RAG检索知识库 → 响应生成
  ├── order → LLM解析 → 直接调用订单服务 → 响应生成
  ├── casual → 闲聊回复
  └── non_coffee → 拒绝回答
```

- 上下文压缩：保留最近 5 条对话，之前的内容由 LLM 压缩为摘要
- 订单自动完成：下单后随机 10~30 秒自动标记为已完成（模拟咖啡制作时间）
- 知识库引用：FAQ 从知识库检索后直接提供给 LLM 回答，不再询问用户已给信息

## 配置

### 环境变量

| 变量                  | 说明                      | 默认值                     |
|---------------------|-------------------------|-------------------------|
| `OPENAI_API_KEY`    | DeepSeek / OpenAI API Key | —                       |
| `DASHSCOPE_API_KEY` | DashScope 嵌入模型 API Key | —                       |
| `MODEL_NAME`        | Chat 模型名                | `deepseek-v4-flash`     |

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.deepseek.com
      chat:
        options:
          model: ${MODEL_NAME:deepseek-v4-flash}
    embedding:
      openai:
        api-key: ${DASHSCOPE_API_KEY}
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        options:
          model: text-embedding-v3
```

### MySQL

服务部署

```shell
docker pull mysql:8.0.32
```

```shell
docker run --name mysql \
-v data_mysql:/var/lib/mysql \
-p 3306:3306 \
-e MYSQL_ROOT_PASSWORD=app_password \
-d mysql:8.0.32
```

初始化

```sql
DROP TABLE IF EXISTS t_message;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_user;

-- t_user：用户表（主键自增，手机号唯一）
CREATE TABLE t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID（主键）',
    name        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '用户姓名',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_phone (phone) COMMENT '手机号唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- t_message：聊天消息表（关联用户ID）
CREATE TABLE t_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    role        VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '角色：user/assistant/system',
    content     TEXT         NOT NULL COMMENT '消息内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_id (user_id) COMMENT '用户ID索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- t_order：订单表（订单号唯一，支持按用户/状态查询）
CREATE TABLE t_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_number VARCHAR(64)  NOT NULL COMMENT '订单编号',
    user_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',
    user_name    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '用户姓名',
    user_phone   VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '用户手机号',
    price        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
    item_name    VARCHAR(200) NOT NULL DEFAULT '' COMMENT '商品名称',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '订单状态：0待支付 1已支付 2已完成 3已取消',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_number (order_number) COMMENT '订单号唯一索引',
    KEY idx_user_id (user_id) COMMENT '用户ID索引',
    KEY idx_status (status) COMMENT '状态索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
```

### Redis-stack

服务部署

```shell
docker pull redis/redis-stack:7.4.0-v8-arm64
```

```shell
docker run -d \
  --name redis-stack \
  --restart=unless-stopped \
  -p 6379:6379 \
  -v redis-stack-data:/data \
  -e REDIS_PASSWORD="app_password" \
  -e REDIS_ARGS="--appendonly yes --requirepass app_password" \
  redis/redis-stack:7.4.0-v8-arm64
```

## API 接口

### 聊天

| 方法   | 路径                      | 说明          | 响应                  |
|------|-------------------------|-------------|---------------------|
| POST | `/workflows/run`        | 非流式对话（全量返回） | `R<String>`         |
| POST | `/workflows/run/stream` | 流式 SSE 对话    | `text/event-stream` |

### 用户

| 方法   | 路径                          | 说明     |
|------|-----------------------------|--------|
| POST | `/user/register`            | 注册     |
| POST | `/user/login`               | 登录     |
| GET  | `/user/{id}/history`        | 获取历史消息 |
| DELETE | `/user/{id}/history`      | 删除历史消息 |

### 订单

| 方法   | 路径               | 说明     |
|------|------------------|--------|
| POST | `/order/list`    | 订单列表   |
| POST | `/order/complete` | 完成订单   |
| POST | `/order/cancel`  | 取消订单   |
| POST | `/order/detail`  | 订单详情   |
| POST | `/order/create`  | 创建订单   |
