CREATE TABLE IF NOT EXISTS movie_embeddings (
                                                movie_id BIGINT NOT NULL,

                                                model_name VARCHAR(100) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    dimensions INT NOT NULL,
    embedding_json JSON NOT NULL,

    created_at DATETIME(6) NOT NULL
    DEFAULT CURRENT_TIMESTAMP(6),

    updated_at DATETIME(6) NOT NULL
    DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (movie_id),

    CONSTRAINT fk_movie_embeddings_movie
    FOREIGN KEY (movie_id)
    REFERENCES movies(id)
    ON DELETE CASCADE,

    CONSTRAINT chk_movie_embeddings_dimensions
    CHECK (dimensions > 0),

    INDEX idx_movie_embeddings_model
(model_name)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4;