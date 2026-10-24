package com.example.thesis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.thesis.auth.JwtService;
import com.example.thesis.common.ApiException;
import com.example.thesis.dto.LoginRequest;
import com.example.thesis.dto.LoginResponse;
import com.example.thesis.dto.UserView;
import com.example.thesis.entity.User;
import com.example.thesis.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthService {

    private static final Set<String> SUPPORTED_ROLES = Set.of("STUDENT", "ADVISOR");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        if (username.isEmpty() || request.password() == null) {
            throw ApiException.badRequest("请输入用户名和密码");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 不区分“账号不存在”和“密码错误”，避免泄露账号是否存在。
            throw ApiException.unauthorized("用户名或密码错误");
        }
        if (!SUPPORTED_ROLES.contains(user.getRole())) {
            throw ApiException.forbidden("当前账号角色不可用");
        }

        String token = jwtService.createToken(user);
        return new LoginResponse(token, jwtService.getExpirationSeconds(), UserView.from(user));
    }

    public UserView currentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ApiException.unauthorized("用户不存在，请重新登录");
        }
        return UserView.from(user);
    }
}
