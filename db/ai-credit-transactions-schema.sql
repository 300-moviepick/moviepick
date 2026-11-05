CREATE TABLE ai_credit_transactions (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        wallet_id BIGINT NOT NULL,
                                        payment_id BIGINT NULL,
                                        request_key CHAR(36) NULL,
                                        event_type VARCHAR(24) NOT NULL,
                                        bucket VARCHAR(12) NOT NULL,
                                        delta INT NOT NULL,
                                        idempotency_key VARCHAR(150) NOT NULL,
                                        created_at DATETIME(6) NOT NULL,

                                        PRIMARY KEY (id),

                                        CONSTRAINT uk_ai_credit_tx_idempotency
                                            UNIQUE (idempotency_key),

                                        INDEX idx_ai_credit_tx_wallet_created
                                            (wallet_id, created_at),

                                        INDEX idx_ai_credit_tx_wallet_request
                                            (wallet_id, request_key),

                                        INDEX idx_ai_credit_tx_event_created
                                            (event_type, created_at),

                                        INDEX idx_ai_credit_tx_payment (payment_id),

                                        CONSTRAINT fk_ai_credit_tx_wallet
                                            FOREIGN KEY (wallet_id)
                                                REFERENCES ai_credit_wallets(id),

                                        CONSTRAINT fk_ai_credit_tx_payment
                                            FOREIGN KEY (payment_id)
                                                REFERENCES payments(id),

                                        CONSTRAINT chk_ai_credit_tx_bucket
                                            CHECK (bucket IN ('FREE', 'PAID')),

                                        CONSTRAINT chk_ai_credit_tx_delta
                                            CHECK (
                                                (event_type IN ('FREE_GRANT', 'PURCHASE') AND delta > 0)
                                                    OR (event_type = 'USE' AND delta = -1)
                                                    OR (event_type = 'RESTORE' AND delta = 1)
                                                    OR (event_type = 'COMPLETE' AND delta = 0)
                                                )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;