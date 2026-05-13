package cn.sg.intelligentcustomerservice.service;


import cn.sg.intelligentcustomerservice.model.User;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * Created on 2025/11/8.
 *
 */
@Service
@AllArgsConstructor
public class UserApplicationService {
    private final UserDomainService userDomainService;

    public User register(String name, String phone) {
        return userDomainService.register(name, phone);
    }

    public User login(String name, String phone) {
        return userDomainService.login(name, phone);
    }
}
