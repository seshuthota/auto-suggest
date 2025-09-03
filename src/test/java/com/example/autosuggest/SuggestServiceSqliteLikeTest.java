package com.example.autosuggest;

import com.example.autosuggest.model.Suggestion;
import com.example.autosuggest.service.SuggestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SuggestServiceSqliteLikeTest {

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    SuggestService service;

    @BeforeEach
    void setupData() {
        // Clean tables
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());

        // Insert deterministic test data
        insert(101, "Alphabet");
        insert(102, "Alpine");
        insert(103, "Alphanumeric");
        insert(104, "Beta");
        insert(105, "Gamma");

        // Rebuild FTS from base content (keeps sqlite-fts path consistent)
        jdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    private void insert(int id, String name) {
        jdbc.update("INSERT INTO people(id, name) VALUES(:id, :name)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name));
    }

    @Test
    void prefixSuggest_returnsExpected() {
        List<Suggestion> out = service.suggest("alp", 10, SuggestService.Mode.PREFIX);
        List<String> values = out.stream().map(Suggestion::value).toList();

        assertThat(values).contains("Alphabet", "Alpine", "Alphanumeric");
        assertThat(values).doesNotContain("Beta", "Gamma");
        assertThat(values.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void containsSuggest_includesMiddleMatches() {
        List<Suggestion> out = service.suggest("bet", 10, SuggestService.Mode.CONTAINS);
        List<String> values = out.stream().map(Suggestion::value).toList();

        // Matches: Alphabet (contains 'bet'), Beta (exact)
        assertThat(values).contains("Alphabet", "Beta");
    }
}

