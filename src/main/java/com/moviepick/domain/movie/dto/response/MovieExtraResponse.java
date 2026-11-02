package com.moviepick.domain.movie.dto.response;

import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;

import java.util.List;
import java.util.Objects;

public record MovieExtraResponse(
        Long movieId,
        Trailer trailer,
        List<Cast> cast,
        List<Director> directors,
        List<OttProvider> streaming,
        List<OttProvider> rent,
        List<OttProvider> buy,
        String watchLink
) {

    public static MovieExtraResponse from(
            Long movieId,
            TmdbMovieResponse response
    ) {
        Trailer trailer = null;

        if (response.videos() != null
                && response.videos().results() != null) {

            trailer = response.videos().results().stream()
                    .filter(Objects::nonNull)
                    .filter(video ->
                            "YouTube".equalsIgnoreCase(video.site())
                                    && "Trailer".equalsIgnoreCase(video.type())
                                    && video.key() != null
                                    && !video.key().isBlank()
                    )
                    .findFirst()
                    .map(video -> new Trailer(
                            video.name(),
                            video.key(),
                            video.official()
                    ))
                    .orElse(null);
        }

        List<Cast> cast = List.of();
        List<Director> directors = List.of();

        if (response.credits() != null) {
            if (response.credits().cast() != null) {
                cast = response.credits().cast().stream()
                        .filter(Objects::nonNull)
                        .limit(10)
                        .map(actor -> new Cast(
                                actor.id(),
                                actor.name(),
                                actor.character(),
                                actor.profilePath()
                        ))
                        .toList();
            }

            if (response.credits().crew() != null) {
                directors = response.credits().crew().stream()
                        .filter(Objects::nonNull)
                        .filter(crew -> "Director".equalsIgnoreCase(crew.job()))
                        .map(crew -> new Director(
                                crew.id(),
                                crew.name()
                        ))
                        .toList();
            }
        }

        TmdbMovieResponse.CountryProvidersResponse korea = null;

        if (response.watchProviders() != null
                && response.watchProviders().results() != null) {
            korea = response.watchProviders().results().get("KR");
        }

        return new MovieExtraResponse(
                movieId,
                trailer,
                cast,
                directors,
                korea == null ? List.of() : toProviders(korea.flatrate()),
                korea == null ? List.of() : toProviders(korea.rent()),
                korea == null ? List.of() : toProviders(korea.buy()),
                korea == null ? null : korea.link()
        );
    }

    private static List<OttProvider> toProviders(
            List<TmdbMovieResponse.ProviderResponse> providers
    ) {
        if (providers == null) {
            return List.of();
        }

        return providers.stream()
                .filter(Objects::nonNull)
                .map(provider -> new OttProvider(
                        provider.providerId(),
                        provider.providerName(),
                        provider.logoPath()
                ))
                .toList();
    }

    public record Trailer(
            String name,
            String youtubeKey,
            Boolean official
    ) {
    }

    public record Cast(
            Long id,
            String name,
            String character,
            String profilePath
    ) {
    }

    public record Director(
            Long id,
            String name
    ) {
    }

    public record OttProvider(
            Integer providerId,
            String providerName,
            String logoPath
    ) {
    }
}
