package cn.sg.intelligentcustomerservice.service;

import cn.sg.intelligentcustomerservice.mapper.UserMapper;
import cn.sg.intelligentcustomerservice.model.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户领域服务
 */
@Service
@RequiredArgsConstructor
public class UserDomainService {

    private final UserMapper userMapper;

    public User register(String name, String phone) {
        User user = User.create(name, phone);
        userMapper.insert(user);
        return user;
    }

    public User get(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 登录（弱验证：用户名+手机号匹配即视为同一用户）
     */
    public User login(String name, String phone) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getName, name)
                        .eq(User::getPhone, phone));
    }
}
