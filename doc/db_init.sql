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