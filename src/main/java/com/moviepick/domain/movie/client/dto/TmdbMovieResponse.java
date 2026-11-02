
package com.moviepick.domain.movie.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieResponse(
        Long id,
        String title,

        @JsonProperty("original_title")
        String originalTitle,

        @JsonProperty("original_language")
        String originalLanguage,

        String overview,

        @JsonProperty("release_date")
        String releaseDate,

        Integer runtime,
        String status,
        Boolean adult,

        @JsonProperty("vote_average")
        Double voteAverage,

        @JsonProperty("vote_count")
        Integer voteCount,

        Double popularity,

        @JsonProperty("poster_path")
        String posterPath,

        @JsonProperty("backdrop_path")
        String backdropPath,

        List<GenreResponse> genres,

        VideosResponse videos,
        CreditsResponse credits,

        @JsonProperty("watch/providers")
        WatchProvidersResponse watchProviders
) {

    // 기존 15개 인자 생성 코드와의 호환성 유지
    public TmdbMovieResponse(
            Long id,
            String title,
            String originalTitle,
            String originalLanguage,
            String overview,
            String releaseDate,
            Integer runtime,
            String status,
            Boolean adult,
            Double voteAverage,
            Integer voteCount,
            Double popularity,
            String posterPath,
            String backdropPath,
            List<GenreResponse> genres
    ) {
        this(
                id, title, originalTitle, originalLanguage,
                overview, releaseDate, runtime, status,
                adult, voteAverage, voteCount, popularity,
                posterPath, backdropPath, genres,
                null, null, null
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenreResponse(
            Integer id,
            String name
    ) {
    }

    // 예고편 및 영상
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideosResponse(
            List<VideoResponse> results
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoResponse(
            String id,
            String name,
            String key,
            String site,
            String type,
            Boolean official,

            @JsonProperty("iso_639_1")
            String language
    ) {
    }

    // 배우 및 제작진
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreditsResponse(
            List<CastResponse> cast,
            List<CrewResponse> crew
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CastResponse(
            Long id,
            String name,
            String character,
            Integer order,

            @JsonProperty("profile_path")
            String profilePath
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CrewResponse(
            Long id,
            String name,
            String job,
            String department,

            @JsonProperty("profile_path")
            String profilePath
    ) {
    }

    // 국가별 OTT 제공 정보
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WatchProvidersResponse(
            Map<String, CountryProvidersResponse> results
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryProvidersResponse(
            String link,
            List<ProviderResponse> flatrate,
            List<ProviderResponse> rent,
            List<ProviderResponse> buy,
            List<ProviderResponse> free,
            List<ProviderResponse> ads
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderResponse(
            @JsonProperty("provider_id")
            Integer providerId,

            @JsonProperty("provider_name")
            String providerName,

            @JsonProperty("logo_path")
            String logoPath
    ) {
    }
}
