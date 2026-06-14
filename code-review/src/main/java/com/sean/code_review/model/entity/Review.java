package com.sean.code_review.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "pr_title")
    private String prTitle;

    @Column(name = "status")
    private String status = "pending";

    /**
     * GitHub App installation ID extracted from the webhook payload
     * (installation.id). Null for reviews created via the manual API endpoint.
     * When null or 0 the services fall back to the PAT token.
     */
    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReviewComment> comments;
}
