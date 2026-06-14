package com.sean.code_review.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class AuthController {

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> a = principal.getAttributes();
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id",             a.get("id") != null ? ((Number) a.get("id")).longValue() : 0L);
        response.put("githubId",       String.valueOf(a.get("id")));
        response.put("githubUsername", a.getOrDefault("login", ""));
        response.put("email",          a.get("email") != null ? a.get("email") : "");
        response.put("avatarUrl",      a.getOrDefault("avatar_url", ""));
        response.put("createdAt",      Instant.now().toString());
        return ResponseEntity.ok(response);
    }
}
