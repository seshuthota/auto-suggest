package com.example.autosuggest;

import com.example.autosuggest.model.TrackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SuggestPopularityLoggingTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());
        insert(11, "MicroA", 1);
        insert(12, "MicroB", 5);
        insert(13, "Micros", 50); // different length; shouldn't trump length-first ordering
        jdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    void insert(int id, String name, int pop) {
        jdbc.update("INSERT INTO people(id, name, popularity) VALUES(:id, :name, :pop)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("pop", pop));
    }

    @Test
    void tracking_increments_popularity_and_affects_order_for_ties() throws Exception {
        // Ensure MicroB ranks before MicroA initially for prefix 'micro'
        var res1 = mvc.perform(get("/suggest").param("q", "micro").param("mode", "PREFIX"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(res1.indexOf("MicroB")).isLessThan(res1.indexOf("MicroA"));

        // Track MicroA 10 times (by value), making it more popular
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/suggest/track")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"value\":\"MicroA\"}"))
                    .andExpect(status().isAccepted());
        }

        // Now MicroA should rank before MicroB (same length tie broken by popularity)
        var res2 = mvc.perform(get("/suggest").param("q", "micro").param("mode", "PREFIX"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(res2.indexOf("MicroA")).isLessThan(res2.indexOf("MicroB"));
    }
}

