package com.moviepick.domain.ai.recommendation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "movie.embedding.schedule",
        name = "enabled",
        havingValue = "true"
)
public class AiEmbeddingSchedulingConfig {
}
