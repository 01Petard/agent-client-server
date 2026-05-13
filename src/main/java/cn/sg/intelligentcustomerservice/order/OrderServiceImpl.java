package cn.sg.intelligentcustomerservice.order;

import cn.sg.intelligentcustomerservice.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现
 * 落库 + 自动完成（下单后随机 10~30 秒自动完成）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final TaskScheduler taskScheduler;

    @Override
    public String createOrder(String userId, String userName, String userPhone,
                              String price, String itemName) {
        log.info("创建订单 - userId: {}, itemName: {}, price: {}", userId, itemName, price);
        OrderDO order = OrderDO.create(userId, userName, userPhone, price, itemName);
        orderMapper.insert(order);
        log.info("订单创建成功 - orderNumber: {}", order.getOrderNumber());

        // 随机 10~30 秒后自动完成
        long delay = ThreadLocalRandom.current().nextLong(10_000, 31_000);
        String orderNumber = order.getOrderNumber();
        taskScheduler.schedule(() -> {
            log.info("自动完成订单: {}", orderNumber);
            complete(orderNumber);
        }, Instant.now().plusMillis(delay));

        return order.toStr();
    }

    @Override
    public String orderDetail(String orderNumber) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderNumber, orderNumber));
        return Optional.ofNullable(order)
                .map(OrderDO::toStr)
                .orElse("未查询到订单信息");
    }

    @Override
    public String cancel(String orderNumber) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderNumber, orderNumber));
        if (order == null) {
            return "未查询到订单信息";
        }
        if (order.isRefund()) {
            return "订单已退款";
        }
        if (order.isCompleted()) {
            return "订单已完成，不允许退款";
        }
        order.cancel();
        orderMapper.updateById(order);
        return "SUCCESS";
    }

    @Override
    public List<OrderDO> orders() {
        return orderMapper.selectList(
                new LambdaQueryWrapper<OrderDO>()
                        .orderByDesc(OrderDO::getCreateTime));
    }

    @Override
    public void complete(String orderNumber) {
        OrderDO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getOrderNumber, orderNumber));
        if (order != null) {
            order.complete();
            orderMapper.updateById(order);
            log.info("订单已完成: {}", orderNumber);
        }
    }

    @Override
    public List<OrderDO> userOrder(String userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<OrderDO>()
                        .eq(OrderDO::getUserId, userId)
                        .orderByDesc(OrderDO::getCreateTime));
    }
}
