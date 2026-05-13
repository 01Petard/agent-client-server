package cn.sg.intelligentcustomerservice.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Created on 2025/11/8.
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    /** user / assistant */
    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public static Message ofUser(String userId, String content) {
        return Message.builder()
                .userId(userId)
                .role("user")
                .content(content)
                .build();
    }

    public static Message ofAssistant(String userId, String content) {
        return Message.builder()
                .userId(userId)
                .role("assistant")
                .content(content)
                .build();
    }
}
