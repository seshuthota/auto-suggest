package com.example.autosuggest.service;

import com.example.autosuggest.model.Suggestion;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
    private final MeterRegistry meter;

    public SuggestService(NamedParameterJdbcTemplate jdbc,
                          @Value("${suggest.engine:sqlite-like}") String engine,
                          @Value("${suggest.cache.enabled:true}") boolean cacheEnabled,
                          @Value("${suggest.defaults.enabled:false}") boolean defaultsEnabled,
                          MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.cacheEnabled = cacheEnabled;
        this.defaultsEnabled = defaultsEnabled;
        this.meter = meterRegistry;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(90))
                .build();
        if (this.meter != null) {
            // register cache metrics
            CaffeineCacheMetrics.monitor(this.meter, this.cache, "suggest.cache");
        }
    }

    @CircuitBreaker(name = "suggest-db", fallbackMethod = "suggestFallback")
    public List<Suggestion> suggest(String q, int limit, Mode mode) {
        String qq = q == null ? "" : q.trim();
        // Guard: avoid empty/very short queries that cause fan-out or errors
        int lim = Math.min(Math.max(limit <= 0 ? 10 : limit, 1), 50);
        if (qq.length() < 2) {
            if (meter != null) {
                meter.counter("suggest.short", "engine", engine, "defaults", String.valueOf(defaultsEnabled)).increment();
            }
            return defaultsEnabled ? defaultPopular(lim) : List.of();
        }
        Mode m = mode == null ? Mode.PREFIX : mode;
        Timer.Sample sample = meter != null ? Timer.start(meter) : null;
        List<Suggestion> result;
        String cacheStatus = "off";
        if (!cacheEnabled) {
            result = dispatch(qq, lim, m);
        } else {
            String keyQ = Normalizer.normalize(qq, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            String key = engine + "|" + m + "|" + keyQ + "|" + lim;
            List<Suggestion> existing = cache.getIfPresent(key);
            if (existing != null) {
                result = existing;
                cacheStatus = "hit";
            } else {
                result = dispatch(qq, lim, m);
                cache.put(key, result);
                cacheStatus = "miss";
            }
        }
        if (meter != null && sample != null) {
            sample.stop(Timer.builder("suggest.query")
                    .tags("engine", engine, "mode", m.name().toLowerCase(), "cache", cacheStatus)
                    .register(meter));
            meter.counter("suggest.results", "engine", engine, "mode", m.name().toLowerCase()).increment(result.size());
        }
        return result;
    }

    // Fallback used by CircuitBreaker: return empty list on DB failures
    @SuppressWarnings("unused")
    public List<Suggestion> suggestFallback(String q, int limit, Mode mode, Throwable t) {
        if (meter != null) {
            meter.counter("suggest.fallback", "engine", engine).increment();
        }
        return List.of();
    }

    public List<Suggestion> defaultSuggestions(int limit) {
        int lim = Math.min(Math.max(limit <= 0 ? 10 : limit, 1), 50);
        if (!defaultsEnabled) return List.of();
        return defaultPopular(lim);
    }

    private List<Suggestion> dispatch(String q, int limit, Mode mode) {
        return switch (engine) {
            case "sqlite-fts" -> suggestSqliteFts(q, limit, mode);
            case "oracle-text" -> suggestOracleText(q, limit, mode);
            case "sqlite-like" -> suggestSqliteLike(q, limit, mode);
            default -> suggestSqliteLike(q, limit, mode);
        };
    }

    public int trackSelection(Integer id, String value) {
        // Update popularity counter by id (preferred) or by case-insensitive value match
        if (id == null && (value == null || value.isBlank())) {
            throw new IllegalArgumentException("Provide either 'id' or non-empty 'value'");
        }
        if (id != null) {
            int updated = jdbc.update("UPDATE people SET popularity = popularity + 1 WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", id));
            if (updated == 0) throw new IllegalArgumentException("No record with id=" + id);
            return updated;
        } else {
            int updated = jdbc.update("UPDATE people SET popularity = popularity + 1 WHERE name = :name COLLATE NOCASE",
                    new MapSqlParameterSource().addValue("name", value));
            if (updated == 0) throw new IllegalArgumentException("No record with value='" + value + "'");
            return updated;
        }
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
        String expr = buildOracleTextExpr(q, mode);
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

    private static String sanitizeOracleText(String s) {
        if (s == null) return "";
        // Remove special query operators; we'll add wildcards explicitly.
        return s.replaceAll("[\\\"'{}\\[\\]\\()|&!~*?:;,.<>+=%-]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String buildOracleTextExpr(String q, Mode mode) {
        String cleaned = sanitizeOracleText(q);
        if (cleaned.isBlank()) return "%"; // match nothing useful; upstream guard prevents this for main path
        String[] terms = cleaned.split("\\s+");
        List<String> parts = new ArrayList<>();
        switch (mode) {
            case PREFIX -> {
                for (int i = 0; i < terms.length; i++) {
                    String t = terms[i];
                    boolean last = (i == terms.length - 1);
                    parts.add(last ? t + "%" : t);
                }
                return String.join(" AND ", parts);
            }
            case CONTAINS -> {
                for (String t : terms) {
                    parts.add("%" + t + "%");
                }
                return String.join(" AND ", parts);
            }
            case FUZZY -> {
                for (String t : terms) {
                    parts.add("fuzzy(" + t + ",70,200,weight)");
                }
                return String.join(" AND ", parts);
            }
            default -> {
                return cleaned + "%";
            }
        }
    }
}
