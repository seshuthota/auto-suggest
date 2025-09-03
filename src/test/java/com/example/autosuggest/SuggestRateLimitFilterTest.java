package com.example.autosuggest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "suggest.ratelimit.enabled=true",
        "suggest.ratelimit.capacity=2",
        "suggest.ratelimit.refillTokens=2",
        "suggest.ratelimit.refillPeriod=PT1H"  // effectively no refill during test
})
class SuggestRateLimitFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void third_request_is_rate_limited() throws Exception {
        var req = get("/suggest").param("q", "micro").header("X-Client-Id", "test-client");
        mvc.perform(req).andExpect(status().isOk());
        mvc.perform(req).andExpect(status().isOk());
        mvc.perform(req).andExpect(status().isTooManyRequests());
    }
}

