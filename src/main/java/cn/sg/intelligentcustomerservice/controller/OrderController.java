package cn.sg.intelligentcustomerservice.controller;

import cn.sg.intelligentcustomerservice.order.OrderDO;
import cn.sg.intelligentcustomerservice.order.OrderService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单 REST 控制器
 * 前端后台页面直接调用，不再通过 Feign 代理外部服务
 */
@Slf4j
@RestController
@RequestMapping("order")
@AllArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("list")
    public List<OrderDO> list() {
        return orderService.orders();
    }

    @PostMapping("complete")
    public String complete(@RequestBody CompleteOrderRequest request) {
        orderService.complete(request.orderNumber());
        return "SUCCESS";
    }

    @PostMapping("cancel")
    public String cancel(@RequestBody CancelOrderRequest request) {
        return orderService.cancel(request.orderNumber());
    }

    @PostMapping("detail")
    public String detail(@RequestBody DetailOrderRequest request) {
        return orderService.orderDetail(request.orderNumber());
    }

    @PostMapping("create")
    public String create(@RequestBody CreateOrderRequest request) {
        return orderService.createOrder(
                request.userId(), request.userName(), request.userPhone(),
                request.price(), request.itemName());
    }

    public record CompleteOrderRequest(String orderNumber) {}
    public record CancelOrderRequest(String orderNumber) {}
    public record DetailOrderRequest(String orderNumber) {}
    public record CreateOrderRequest(String userId, String userName, String userPhone,
                                     String price, String itemName) {}
}
