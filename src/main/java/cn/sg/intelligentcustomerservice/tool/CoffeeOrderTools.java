package cn.sg.intelligentcustomerservice.tool;

import cn.sg.intelligentcustomerservice.order.OrderDO;
import cn.sg.intelligentcustomerservice.order.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 咖啡订单 AI 工具
 * 通过 @Tool 注解暴露给 ChatClient，由 AI 按需调用
 * 集成自订单 MCP 服务的全部功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoffeeOrderTools {

    private final OrderService orderService;

    @Tool(description = "下单:通过用户ID、用户姓名、手机号、商品价格、商品名称创建一笔订单，返回订单详情")
    public String createOrder(
            @ToolParam(description = "用户ID") String userId,
            @ToolParam(description = "用户姓名") String userName,
            @ToolParam(description = "用户手机号") String userPhone,
            @ToolParam(description = "商品价格") String price,
            @ToolParam(description = "商品名称") String itemName) {
        log.info("AI 调用: 创建订单 userId={}, itemName={}", userId, itemName);
        return orderService.createOrder(userId, userName, userPhone, price, itemName);
    }

    @Tool(description = "查询所有订单列表，返回所有订单的详细信息（订单号、用户、商品、价格、状态、时间）")
    public List<OrderDO> queryAllOrders() {
        log.info("AI 调用: 查询所有订单");
        return orderService.orders();
    }

    @Tool(description = "订单详情:通过订单号查询订单详情")
    public String orderDetail(@ToolParam(description = "订单号") String orderNumber) {
        log.info("AI 调用: 查询订单详情 {}", orderNumber);
        return orderService.orderDetail(orderNumber);
    }

    @Tool(description = "用户订单查询:查询某个用户下有哪些订单")
    public List<OrderDO> userOrder(@ToolParam(description = "用户ID") String userId) {
        log.info("AI 调用: 查询用户订单 {}", userId);
        return orderService.userOrder(userId);
    }

    @Tool(description = "通过订单号完成订单，标记订单状态为已完成")
    public String completeOrder(@ToolParam(description = "订单号") String orderNumber) {
        log.info("AI 调用: 完成订单 {}", orderNumber);
        orderService.complete(orderNumber);
        return "订单 " + orderNumber + " 已完成";
    }

    @Tool(description = "通过订单号取消订单，执行退款操作")
    public String cancelOrder(@ToolParam(description = "订单号") String orderNumber) {
        log.info("AI 调用: 取消订单 {}", orderNumber);
        return orderService.cancel(orderNumber);
    }
}
