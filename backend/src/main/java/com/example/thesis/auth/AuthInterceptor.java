package com.example.thesis.auth;

import com.example.thesis.common.ApiResponse;
import com.example.thesis.dto.UserView;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录与角色拦截：
 * - /api/student/** 仅允许 STUDENT；
 * - /api/advisor/** 仅允许 ADVISOR；
 * - 其他 /api/** 只要求登录。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return false;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            UserView user = jwtService.parseToken(token);
            if (path.startsWith("/api/student/") && !"STUDENT".equals(user.role())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "当前账号无权访问学生接口");
                return false;
            }
            if (path.startsWith("/api/advisor/") && !"ADVISOR".equals(user.role())) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "当前账号无权访问导师接口");
                return false;
            }
            AuthContext.set(request, user);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录状态已失效，请重新登录");
            return false;
        }
    }

    private boolean isPublicPath(String path) {
        return "/api/auth/login".equals(path) || "/api/health".equals(path);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(status, message));
    }
}
