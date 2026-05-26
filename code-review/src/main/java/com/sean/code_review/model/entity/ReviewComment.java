package com.sean.code_review.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "review_comments")
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "severity")
    private String severity;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "posted_to_github")
    private Boolean postedToGithub = false;
}