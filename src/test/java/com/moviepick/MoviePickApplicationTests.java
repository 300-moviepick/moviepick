package com.moviepick;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "JWT_SECRET=bW92aWVwaWNrLXRlc3Qtb25seS1qd3Qtc2VjcmV0LWtleS1mb3ItY2khIQ==",
        "TMDB_ACCESS_TOKEN=test-dummy-tmdb-token"
})
class MoviePickApplicationTests {

    @Test
    void contextLoads() {
    }
}