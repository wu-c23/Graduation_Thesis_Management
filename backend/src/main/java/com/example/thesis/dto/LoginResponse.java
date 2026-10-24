package com.example.thesis.dto;

public record LoginResponse(String token, long expiresInSeconds, UserView user) {
}
