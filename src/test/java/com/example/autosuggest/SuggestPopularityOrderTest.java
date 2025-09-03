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
class SuggestPopularityOrderTest {

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    SuggestService service;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM people", new MapSqlParameterSource());
        jdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());

        // same length strings so popularity affects tie-breaker
        insert(1, "MicroX", 10);
        insert(2, "MicroY", 50);
        insert(3, "MacroZ", 100);

        jdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    private void insert(int id, String name, int popularity) {
        jdbc.update("INSERT INTO people(id, name, popularity) VALUES(:id, :name, :pop)",
                new MapSqlParameterSource().addValue("id", id)
                        .addValue("name", name)
                        .addValue("pop", popularity));
    }

    @Test
    void like_prefix_ordersByPopularityOnTies() {
        List<Suggestion> out = service.suggest("micr", 10, SuggestService.Mode.PREFIX);
        List<String> vals = out.stream().map(Suggestion::value).toList();
        assertThat(vals.indexOf("MicroY")).isLessThan(vals.indexOf("MicroX"));
    }
}

