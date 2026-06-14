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

    //columnDefinition = "TEXT" — for comment/suggestion,
    // makes the DB column allow long text (regular VARCHAR has a length limit, TEXT doesn't).

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;
    // postedToGithub = false — tracks whether this comment has actually been posted back to GitHub yet (default: not yet)
    @Column(name = "posted_to_github")
    private Boolean postedToGithub = false;
}