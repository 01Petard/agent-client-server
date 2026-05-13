package cn.sg.intelligentcustomerservice.order;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class OrderDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_number")
    private String orderNumber;

    @TableField("user_id")
    private String userId;

    @TableField("user_name")
    private String userName;

    @TableField("user_phone")
    private String userPhone;

    @TableField("price")
    private String price;

    @TableField("item_name")
    private String itemName;

    @TableField("status")
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public static OrderDO create(String userId, String userName, String userPhone,
                                 String price, String itemName) {
        OrderDO order = new OrderDO();
        order.setOrderNumber("ORDER" + System.currentTimeMillis());
        order.setUserId(userId);
        order.setUserName(userName);
        order.setUserPhone(userPhone);
        order.setPrice(price);
        order.setItemName(itemName);
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        return order;
    }

    public String toStr() {
        String statusStr = status == 0 ? "制作中" : status == 1 ? "已完成" : "已退款";
        return "订单号：" + orderNumber + "\n" +
                "用户姓名：" + userName + "\n" +
                "用户手机号：" + userPhone + "\n" +
                "商品名称：" + itemName + "\n" +
                "价格：" + price + "\n" +
                "订单状态：" + statusStr + "\n" +
                "创建时间：" + createTime + "\n" +
                "更新时间：" + updateTime;
    }

    public boolean isCompleted() { return status == 1; }
    public boolean isRefund()    { return status == 2; }

    public void cancel()   { this.status = 2; }
    public void complete() { this.status = 1; }
}
