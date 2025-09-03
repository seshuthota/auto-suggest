package com.example.autosuggest;

import com.example.autosuggest.model.Suggestion;
import com.example.autosuggest.service.SuggestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "suggest.engine=sqlite-fts")
class SuggestServiceSqliteFtsTest {

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    SuggestService service;

    @BeforeEach
    void setupData() {
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());

        insert(301, "Microsoft");
        insert(302, "Microtek");
        insert(303, "Microscope");
        insert(304, "Macrohard");
        insert(305, "Minecraft");

        // Build FTS index from base content
        jdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    private void insert(int id, String name) {
        jdbc.update("INSERT INTO people(id, name) VALUES(:id, :name)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name));
    }

    @Test
    void fts_prefixSuggest_returnsExpected() {
        List<Suggestion> out = service.suggest("micr", 10, SuggestService.Mode.PREFIX);
        List<String> values = out.stream().map(Suggestion::value).toList();

        assertThat(values).contains("Microsoft", "Microtek", "Microscope");
        assertThat(values).doesNotContain("Macrohard");
    }
}

