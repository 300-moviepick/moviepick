
CREATE TABLE IF NOT EXISTS ai_recommendation_feedback (
                                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                                          member_id BIGINT NOT NULL,
                                                          movie_id BIGINT NOT NULL,
                                                          preference VARCHAR(10) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_ai_feedback_member_movie
    UNIQUE (member_id, movie_id),

    INDEX idx_ai_feedback_member_preference
(member_id, preference),

    CONSTRAINT fk_ai_feedback_member
    FOREIGN KEY (member_id) REFERENCES members(id),

    CONSTRAINT fk_ai_feedback_movie
    FOREIGN KEY (movie_id) REFERENCES movies(id),

    CONSTRAINT chk_ai_feedback_preference
    CHECK (preference IN ('LIKE', 'DISLIKE'))
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
