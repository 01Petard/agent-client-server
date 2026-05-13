package cn.sg.intelligentcustomerservice.mapper;

import cn.sg.intelligentcustomerservice.model.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
