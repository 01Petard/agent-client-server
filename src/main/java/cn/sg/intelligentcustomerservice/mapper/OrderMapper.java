package cn.sg.intelligentcustomerservice.mapper;

import cn.sg.intelligentcustomerservice.order.OrderDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {
}
