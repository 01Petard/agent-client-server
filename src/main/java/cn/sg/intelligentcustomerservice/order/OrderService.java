package cn.sg.intelligentcustomerservice.order;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {

    String createOrder(String userId, String userName, String userPhone,
                       String price, String itemName);

    String orderDetail(String orderNumber);

    String cancel(String orderNumber);

    List<OrderDO> orders();

    void complete(String orderNumber);

    List<OrderDO> userOrder(String userId);
}
