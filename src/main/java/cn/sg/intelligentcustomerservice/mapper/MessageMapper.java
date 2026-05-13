package cn.sg.intelligentcustomerservice.mapper;

import cn.sg.intelligentcustomerservice.model.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    List<Message> findAllByUserId(String userId);
}
