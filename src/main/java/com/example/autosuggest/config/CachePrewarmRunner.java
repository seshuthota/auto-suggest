package com.example.autosuggest.config;

import com.example.autosuggest.service.SuggestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CachePrewarmRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CachePrewarmRunner.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final SuggestService suggestService;
    private final boolean enabled;
    private final int prefixLen;
    private final int top;
    private final String prefixesCsv;

    public CachePrewarmRunner(NamedParameterJdbcTemplate jdbc,
                              SuggestService suggestService,
                              @Value("${suggest.cache.prewarm.enabled:false}") boolean enabled,
                              @Value("${suggest.cache.prewarm.prefixLen:3}") int prefixLen,
                              @Value("${suggest.cache.prewarm.top:50}") int top,
                              @Value("${suggest.cache.prewarm.prefixes:}") String prefixesCsv) {
        this.jdbc = jdbc;
        this.suggestService = suggestService;
        this.enabled = enabled;
        this.prefixLen = prefixLen;
        this.top = top;
        this.prefixesCsv = prefixesCsv == null ? "" : prefixesCsv;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        List<String> prefixes = userProvidedPrefixes();
        if (prefixes.isEmpty()) {
            prefixes = deriveHotPrefixes(prefixLen, top);
        }
        int warmed = 0;
        for (String p : prefixes) {
            if (p == null) continue;
            String q = p.trim();
            if (q.length() < 2) continue;
            try {
                suggestService.suggest(q, 10, SuggestService.Mode.PREFIX);
                warmed++;
            } catch (Exception e) {
                log.debug("Prewarm failed for prefix '{}': {}", q, e.getMessage());
            }
        }
        log.info("Cache prewarm completed: warmed {} prefixes", warmed);
    }

    private List<String> userProvidedPrefixes() {
        if (prefixesCsv.isBlank()) return List.of();
        return Arrays.stream(prefixesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> deriveHotPrefixes(int len, int limit) {
        String sql = """
                SELECT lower(substr(name,1,:len)) AS p
                FROM people
                WHERE name IS NOT NULL AND length(name) >= :len
                GROUP BY p
                ORDER BY count(*) DESC
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("len", len).addValue("limit", limit), String.class);
    }
}

