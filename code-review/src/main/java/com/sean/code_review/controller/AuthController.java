package com.sean.code_review.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String login = user.getAttribute("login");
        String avatarUrl = user.getAttribute("avatar_url");

        return ResponseEntity.ok(Map.of(
                "githubUsername", login != null ? login : "",
                "avatarUrl", avatarUrl != null ? avatarUrl : ""
        ));
    }
}
