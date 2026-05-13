-- =============================================================
-- 智能客服系统 数据库初始化脚本
-- =============================================================

-- 用户表：id 改为自增 Long
CREATE TABLE IF NOT EXISTS `t_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `name`        VARCHAR(100) NOT NULL COMMENT '用户名称',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 消息表：user_id 存 Long 的字符串形式
CREATE TABLE IF NOT EXISTS `t_message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `user_id`     VARCHAR(36)  NOT NULL COMMENT '用户ID（Long转字符串）',
    `role`        VARCHAR(20)  NOT NULL DEFAULT '' COMMENT 'user / assistant',
    `content`     TEXT         NOT NULL COMMENT '消息内容',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- 订单表
CREATE TABLE IF NOT EXISTS `t_order` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `order_number` VARCHAR(64)  NOT NULL COMMENT '订单号（ORDER+时间戳）',
    `user_id`      VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '用户ID',
    `user_name`    VARCHAR(100) NOT NULL DEFAULT '' COMMENT '用户姓名',
    `user_phone`   VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '用户手机号',
    `price`        VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '商品价格',
    `item_name`    VARCHAR(200) NOT NULL DEFAULT '' COMMENT '商品名称',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0-制作中 1-已完成 2-已退款',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_number` (`order_number`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
