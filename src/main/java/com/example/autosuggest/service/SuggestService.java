package com.example.autosuggest.service;

import com.example.autosuggest.model.Suggestion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class SuggestService {

    public enum Mode { PREFIX, CONTAINS, FUZZY }

    private final NamedParameterJdbcTemplate jdbc;
    private final String engine;
    private final Cache<String, List<Suggestion>> cache;
    private final boolean cacheEnabled;
    private final boolean defaultsEnabled;

    public SuggestService(NamedParameterJdbcTemplate jdbc,
                          @Value("${suggest.engine:sqlite-like}") String engine,
                          @Value("${suggest.cache.enabled:true}") boolean cacheEnabled,
                          @Value("${suggest.defaults.enabled:false}") boolean defaultsEnabled) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.cacheEnabled = cacheEnabled;
        this.defaultsEnabled = defaultsEnabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(90))
                .build();
    }

    public List<Suggestion> suggest(String q, int limit, Mode mode) {
        String qq = q == null ? "" : q.trim();
        // Guard: avoid empty/very short queries that cause fan-out or errors
        int lim = Math.min(Math.max(limit <= 0 ? 10 : limit, 1), 50);
        if (qq.length() < 2) {
            return defaultsEnabled ? defaultPopular(lim) : List.of();
        }
        Mode m = mode == null ? Mode.PREFIX : mode;
        if (!cacheEnabled) {
            return dispatch(qq, lim, m);
        }
        String keyQ = Normalizer.normalize(qq, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String key = engine + "|" + m + "|" + keyQ + "|" + lim;
        return cache.get(key, k -> dispatch(qq, lim, m));
    }

    private List<Suggestion> dispatch(String q, int limit, Mode mode) {
        return switch (engine) {
            case "sqlite-fts" -> suggestSqliteFts(q, limit, mode);
            case "oracle-text" -> suggestOracleText(q, limit, mode);
            case "sqlite-like" -> suggestSqliteLike(q, limit, mode);
            default -> suggestSqliteLike(q, limit, mode);
        };
    }

    // Option A: Simple prefix/contains via LIKE and NOCASE collation (SQLite)
    private List<Suggestion> suggestSqliteLike(String q, int limit, Mode mode) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", q)
                .addValue("limit", limit);

        if (mode == Mode.PREFIX) {
            sql = """
                    SELECT name AS value
                    FROM people
                    WHERE name LIKE :q || '%' COLLATE NOCASE
                    ORDER BY CASE WHEN name = :q COLLATE NOCASE THEN 0 ELSE 1 END,
                             length(name), popularity DESC, name
                    LIMIT :limit
                    """;
        } else { // CONTAINS or FUZZY fallback
            sql = """
                    SELECT name AS value
                    FROM people
                    WHERE name LIKE '%' || :q || '%' COLLATE NOCASE
                    ORDER BY CASE WHEN name LIKE :q || '%' COLLATE NOCASE THEN 0 ELSE 1 END,
                             length(name), popularity DESC, name
                    LIMIT :limit
                    """;
        }

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.stream()
                .map(r -> new Suggestion(Objects.toString(r.get("value"), null), null))
                .collect(Collectors.toList());
    }

    // Option B: FTS5-powered prefix with ranking (SQLite)
    private List<Suggestion> suggestSqliteFts(String q, int limit, Mode mode) {
        String cleaned = sanitizeFts5(q);
        String match = buildFtsMatch(cleaned, mode);

        String sql = """
                SELECT p.name, bm25(people_fts) AS score
                FROM people_fts f
                JOIN people p ON p.id = f.rowid
                WHERE people_fts MATCH :match
                ORDER BY score, p.popularity DESC, length(p.name), p.name
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("match", match)
                .addValue("limit", limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.stream()
                .map(r -> new Suggestion(
                        Objects.toString(r.get("name"), null),
                        r.get("score") == null ? null : ((Number) r.get("score")).doubleValue()))
                .collect(Collectors.toList());
    }

    // Oracle Text-backed suggestions (CONTAINS) with scoring
    private List<Suggestion> suggestOracleText(String q, int limit, Mode mode) {
        String expr;
        switch (mode) {
            case PREFIX -> expr = escapeOracleText(q) + "%";
            case CONTAINS -> expr = "%" + escapeOracleText(q) + "%";
            case FUZZY -> expr = "fuzzy(" + escapeOracleText(q) + ",70,200,weight)";
            default -> expr = q + "%";
        }
        String sql = """
                SELECT NAME AS value, SCORE(1) AS score
                FROM PEOPLE
                WHERE CONTAINS(NAME, :expr, 1) > 0
                ORDER BY SCORE(1) DESC, LENGTH(NAME), NAME
                FETCH FIRST :limit ROWS ONLY
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("expr", expr)
                .addValue("limit", limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.stream()
                .map(r -> new Suggestion(
                        Objects.toString(r.get("value"), null),
                        r.get("score") == null ? null : ((Number) r.get("score")).doubleValue()))
                .collect(Collectors.toList());
    }

    // --- helpers ---
    private List<Suggestion> defaultPopular(int limit) {
        String sql = """
                SELECT name AS value
                FROM people
                ORDER BY popularity DESC, length(name), name
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.stream()
                .map(r -> new Suggestion(Objects.toString(r.get("value"), null), null))
                .collect(Collectors.toList());
    }
    private static String sanitizeFts5(String s) {
        if (s == null) return "";
        // Remove operators we don't support from user input; normalize spaces
        return s.replaceAll("[\"'()<>~^:+\\-]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String buildFtsMatch(String cleaned, Mode mode) {
        if (cleaned.isBlank()) return ""; // upstream guard ensures length>=2
        String[] terms = cleaned.split("\\s+");
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < terms.length; i++) {
            String t = terms[i];
            if (t.isBlank()) continue;
            boolean last = (i == terms.length - 1);
            // For PREFIX and CONTAINS: last token gets prefix wildcard
            clauses.add(last ? t + "*" : t);
        }
        String match = String.join(" AND ", clauses);
        if (match.isBlank()) match = cleaned + "*";
        return match;
    }

    private static String escapeOracleText(String s) {
        if (s == null || s.isBlank()) return "\"\"";
        String safe = s.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }
}
