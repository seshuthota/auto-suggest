package com.example.autosuggest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SuggestCircuitBreakerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    NamedParameterJdbcTemplate npJdbc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void breakSchema() {
        // Drop the people table to force a DB error during suggest
        jdbc.execute("DROP TABLE IF EXISTS people");
    }

    @AfterEach
    void restoreSchema() {
        // Recreate minimal table to avoid affecting other tests when context is reused
        jdbc.execute("CREATE TABLE IF NOT EXISTS people (id INTEGER PRIMARY KEY, name TEXT, popularity INTEGER DEFAULT 0)");
    }

    @Test
    void suggest_returns_empty_on_db_failure_via_circuit_breaker() throws Exception {
        var res = mvc.perform(get("/suggest").param("q", "micro").param("limit", "5"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Should be an empty JSON array [] from fallback
        assertThat(res.trim()).isEqualTo("[]");
    }
}

