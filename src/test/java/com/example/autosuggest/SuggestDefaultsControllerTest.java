package com.example.autosuggest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "suggest.defaults.enabled=true",
        "suggest.engine=sqlite-like"
})
class SuggestDefaultsControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());
        insert(1, "Alpha", 5);
        insert(2, "Beta", 20);
        insert(3, "Gamma", 10);
        jdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    void insert(int id, String name, int pop) {
        jdbc.update("INSERT INTO people(id, name, popularity) VALUES(:id, :name, :pop)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("pop", pop));
    }

    @Test
    void defaults_returns_popular_sorted() throws Exception {
        mvc.perform(get("/suggest/defaults").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("Beta"))
                .andExpect(jsonPath("$[1].value").value("Gamma"));
    }
}

