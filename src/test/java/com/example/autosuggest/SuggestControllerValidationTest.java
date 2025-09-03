package com.example.autosuggest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SuggestControllerValidationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rejects_short_query() throws Exception {
        mvc.perform(get("/suggest").param("q", "a")).andExpect(status().isBadRequest());
    }

    @Test
    void rejects_limit_too_large() throws Exception {
        mvc.perform(get("/suggest").param("q", "abc").param("limit", "1000"))
                .andExpect(status().isBadRequest());
    }
}

