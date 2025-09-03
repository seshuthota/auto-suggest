package com.example.autosuggest;

import com.example.autosuggest.service.FtsAdminService;
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

@SpringBootTest
@TestPropertySource(properties = {
        "suggest.engine=sqlite-fts",
        "suggest.fts.manage=true"
})
class FtsTriggersIntegrationTest {

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    FtsAdminService fts;

    @Autowired
    SuggestService service;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());
        fts.ensureTriggers();
    }

    @Test
    void insert_into_people_indexes_into_fts_via_trigger() {
        insert(801, "MicroTrigger", 0);
        // No explicit rebuild call; relies on trigger
        List<com.example.autosuggest.model.Suggestion> out = service.suggest("microt", 10, SuggestService.Mode.PREFIX);
        assertThat(out.stream().map(com.example.autosuggest.model.Suggestion::value).toList())
                .contains("MicroTrigger");
    }

    void insert(int id, String name, int pop) {
        jdbc.update("INSERT INTO people(id, name, popularity) VALUES(:id, :name, :pop)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("pop", pop));
    }
}

