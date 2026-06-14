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
        return ResponseEntity.ok(Map.of(
                "id",             ((Number) a.get("id")).longValue(),
                "githubId",       String.valueOf(a.get("id")),
                "githubUsername", a.getOrDefault("login", ""),
                "email",          a.getOrDefault("email", ""),
                "avatarUrl",      a.getOrDefault("avatar_url", ""),
                "createdAt",      Instant.now().toString()
        ));
    }
}
