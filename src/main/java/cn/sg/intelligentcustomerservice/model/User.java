package cn.sg.intelligentcustomerservice.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体（落库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_user")
public class User {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    @TableField("name")
    private String name;

    /** 手机号 */
    @TableField("phone")
    private String phone;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public static User create(String name, String phone) {
        User user = new User();
        user.setName(name);
        user.setPhone(phone);
        user.setCreateTime(LocalDateTime.now());
        return user;
    }

    public String toStr() {
        return "用户ID: " + id + "\n" +
                "用户名：" + name + "\n" +
                "手机号：" + phone;
    }
}
