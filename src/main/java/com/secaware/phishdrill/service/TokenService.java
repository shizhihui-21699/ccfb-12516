package com.secaware.phishdrill.service;

import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 生成每个收件人独立的不可猜测追踪 token（URL-safe，无填充 Base64）。
 */
@Singleton
public class TokenService {

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String newToken() {
        byte[] bytes = new byte[24]; // 192 bit 熵
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
