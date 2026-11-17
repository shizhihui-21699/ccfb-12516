package com.secaware.phishdrill.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    @Test
    void tokensAreUniqueAndUrlSafe() {
        TokenService service = new TokenService();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String token = service.newToken();
            assertEquals(32, token.length()); // 24 字节 base64url 无填充
            assertTrue(token.matches("[A-Za-z0-9_-]+"));
            assertTrue(seen.add(token), "token 出现重复");
        }
        assertFalse(seen.isEmpty());
    }
}
