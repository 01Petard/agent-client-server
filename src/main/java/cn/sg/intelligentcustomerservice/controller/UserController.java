package cn.sg.intelligentcustomerservice.controller;

import cn.sg.intelligentcustomerservice.mapper.MessageMapper;
import cn.sg.intelligentcustomerservice.model.Message;
import cn.sg.intelligentcustomerservice.model.User;
import cn.sg.intelligentcustomerservice.service.UserApplicationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("user")
@AllArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final MessageMapper messageMapper;

    /** 注册 */
    @PostMapping("register")
    public User register(@RequestBody RegisterRequest request) {
        return userApplicationService.register(request.name(), request.phone());
    }

    /** 登录（弱验证） */
    @PostMapping("login")
    public User login(@RequestBody LoginRequest request) {
        User user = userApplicationService.login(request.name(), request.phone());
        if (user == null) {
            throw new RuntimeException("用户不存在，请先注册");
        }
        return user;
    }

    /** 历史消息 */
    @GetMapping("{userId}/history")
    public List<Message> history(@PathVariable Long userId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId)
                        .orderByAsc(Message::getCreateTime));
    }

    /** 删除历史 */
    @DeleteMapping("{userId}/history")
    public String deleteHistory(@PathVariable Long userId) {
        messageMapper.delete(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getUserId, userId));
        return "SUCCESS";
    }

    public record RegisterRequest(String name, String phone) {}
    public record LoginRequest(String name, String phone) {}
}
