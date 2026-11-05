CREATE TABLE ai_credit_wallets (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   member_id BIGINT NOT NULL,
                                   free_available INT NOT NULL DEFAULT 0,
                                   paid_available INT NOT NULL DEFAULT 0,
                                   version BIGINT NOT NULL,
                                   created_at DATETIME(6) NOT NULL,

                                   PRIMARY KEY (id),

                                   CONSTRAINT uk_ai_credit_wallets_member
                                       UNIQUE (member_id),

                                   CONSTRAINT fk_ai_credit_wallets_member
                                       FOREIGN KEY (member_id)
                                           REFERENCES members(id),

                                   CONSTRAINT chk_ai_wallet_free
                                       CHECK (free_available >= 0),

                                   CONSTRAINT chk_ai_wallet_paid
                                       CHECK (paid_available >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;