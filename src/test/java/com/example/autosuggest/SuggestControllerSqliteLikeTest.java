package com.example.autosuggest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SuggestControllerSqliteLikeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @BeforeEach
    void setupData() {
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());

        insert(201, "Microsoft");
        insert(202, "Microtek");
        insert(203, "Microscope");
        insert(204, "Macrohard");
        insert(205, "Minecraft");

        jdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    private void insert(int id, String name) {
        jdbc.update("INSERT INTO people(id, name) VALUES(:id, :name)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name));
    }

    @Test
    void http_prefixSuggest_works() throws Exception {
        mvc.perform(get("/suggest")
                        .param("q", "micr")
                        .param("limit", "10")
                        .param("mode", "PREFIX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.value=='Microsoft')]").exists())
                .andExpect(jsonPath("$[?(@.value=='Microtek')]").exists())
                .andExpect(jsonPath("$[?(@.value=='Microscope')]").exists());
    }
}
