package com.sean.code_review.controller;

import com.sean.code_review.util.WebhookSignatureValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookSignatureValidator signatureValidator;

    public WebhookController(WebhookSignatureValidator signatureValidator) {
        this.signatureValidator = signatureValidator;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGithubWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String payload) {

        if (!signatureValidator.isValid(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        System.out.println("Received GitHub event: " + eventType);
        System.out.println("Payload: " + payload);

        return ResponseEntity.ok("Received");
    }
}