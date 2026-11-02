package com.moviepick.domain.movie.batch.config;

import com.moviepick.domain.movie.batch.tasklet.InitialMovieSyncTasklet;
import com.moviepick.domain.movie.batch.tasklet.MovieSyncTasklet;
import com.moviepick.domain.movie.service.MovieCollectionService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MovieBatchConfig {

    // 정기 영화 수집 Tasklet
    @Bean
    public MovieSyncTasklet movieSyncTasklet(
            MovieCollectionService movieCollectionService,
            @Value("${movie.batch.collect-pages:1}") int collectPages
    ) {
        return new MovieSyncTasklet(movieCollectionService, collectPages);
    }

    // 정기 영화 수집 Step
    @Bean
    public Step movieSyncStep(
            JobRepository jobRepository,
            @Qualifier("movieSyncTasklet") MovieSyncTasklet tasklet
    ) {
        return new StepBuilder("movieSyncStep", jobRepository)
                .tasklet(tasklet)
                .build();
    }

    // 정기 영화 수집 Job
    @Bean
    public Job movieSyncJob(
            JobRepository jobRepository,
            @Qualifier("movieSyncStep") Step movieSyncStep
    ) {
        return new JobBuilder("movieSyncJob", jobRepository)
                .start(movieSyncStep)
                .build();
    }

    // 초기 대량 수집 Tasklet
    @Bean
    public InitialMovieSyncTasklet initialMovieSyncTasklet(
            MovieCollectionService movieCollectionService,
            @Value("${movie.batch.initial-pages:100}") int initialPages
    ) {
        return new InitialMovieSyncTasklet(
                movieCollectionService,
                initialPages
        );
    }

    // 초기 대량 수집 Step
    @Bean
    public Step initialMovieSyncStep(
            JobRepository jobRepository,
            @Qualifier("initialMovieSyncTasklet") InitialMovieSyncTasklet tasklet
    ) {
        return new StepBuilder("initialMovieSyncStep", jobRepository)
                .tasklet(tasklet)
                .build();
    }

    // 초기 대량 수집 Job
    @Bean
    public Job initialMovieSyncJob(
            JobRepository jobRepository,
            @Qualifier("initialMovieSyncStep") Step initialMovieSyncStep
    ) {
        return new JobBuilder("initialMovieSyncJob", jobRepository)
                .start(initialMovieSyncStep)
                .build();
    }
}
