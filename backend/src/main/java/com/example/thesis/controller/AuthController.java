package com.example.thesis.controller;

import com.example.thesis.auth.AuthContext;
import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.LoginRequest;
import com.example.thesis.dto.LoginResponse;
import com.example.thesis.dto.UserView;
import com.example.thesis.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(HttpServletRequest request) {
        return ApiResponse.ok(authService.currentUser(AuthContext.get(request).id()));
    }
}
