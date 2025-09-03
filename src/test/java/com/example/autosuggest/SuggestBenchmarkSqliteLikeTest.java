package com.example.autosuggest;

import com.example.autosuggest.service.SuggestService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "suggest.engine=sqlite-like",
        "suggest.cache.enabled=false"
})
@EnabledIfSystemProperty(named = "bench", matches = "true")
class SuggestBenchmarkSqliteLikeTest {

    @Autowired
    NamedParameterJdbcTemplate npJdbc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SuggestService service;

    private final List<String> prefixes = List.of(
            "mic", "micr", "micro", "ma", "mac", "alp", "bet",
            "gam", "del", "ome", "ze", "zeb", "qu", "qua",
            "app", "amaz", "goog", "meta", "netf", "nvid", "orac", "open"
    );

    @BeforeEach
    void prepare() {
        int target = Integer.getInteger("bench.records", 20000);
        int popMax = Math.max(10, (int) Math.sqrt(target));

        // Tuning for faster inserts on SQLite
        jdbc.execute("PRAGMA journal_mode=MEMORY");
        jdbc.execute("PRAGMA synchronous=OFF");
        jdbc.execute("PRAGMA temp_store=MEMORY");
        jdbc.execute("PRAGMA cache_size=-200000");

        npJdbc.update("DELETE FROM people", new MapSqlParameterSource());
        npJdbc.update("DELETE FROM people_fts", new MapSqlParameterSource());

        // Generate data in batches inside a transaction
        String[] roots = {"Micro", "Macro", "Alpha", "Beta", "Gamma", "Delta", "Omega", "Zebra",
                "Quantum", "Quark", "Apple", "Amazon", "Google", "Meta", "Netflix", "Nvidia",
                "Oracle", "OpenAI", "OpenSearch", "Microscope", "Microsoft", "Microtek", "Microlabs"};
        String[] suffixes = {"soft", "tek", "scope", " lens", " systems", " labs", " corp", " inc",
                " ltd", " group", " holdings", " network", " tech", " solutions", " devices", " energy",
                " data", " cloud", " ai", " robotics", " analytics", " digital"};

        jdbc.execute("BEGIN TRANSACTION");
        try {
            List<Object[]> batch = new ArrayList<>(10_000);
            int id = 1;
            outer:
            for (String r : roots) {
                for (String s : suffixes) {
                    for (int n = 0; n < 3000; n++) { // grows up to roots*suffixes*3000
                        if (id > target) break outer;
                        int pop = (id % popMax);
                        batch.add(new Object[]{id, (r + s + " " + n), pop});
                        if (batch.size() >= 10_000) {
                            jdbc.batchUpdate("INSERT INTO people(id, name, popularity) VALUES(?,?,?)", batch);
                            batch.clear();
                        }
                        id++;
                    }
                }
            }
            if (!batch.isEmpty()) {
                jdbc.batchUpdate("INSERT INTO people(id, name, popularity) VALUES(?,?,?)", batch);
            }
        } finally {
            jdbc.execute("COMMIT");
        }

        // Keep FTS in sync for parity with FTS benchmarks
        npJdbc.update("INSERT INTO people_fts(people_fts) VALUES ('rebuild')", new MapSqlParameterSource());
    }

    @Test
    void runBenchmark() {
        System.out.println("=== Benchmark: sqlite-like (no cache) ===");
        benchOnce();
    }

    private void benchOnce() {
        Random rnd = new Random(42);
        List<Long> nanos = new ArrayList<>();

        int warm = Integer.getInteger("bench.warm", 200);
        for (int i = 0; i < warm; i++) {
            String q = prefixes.get(rnd.nextInt(prefixes.size()));
            service.suggest(q, 10, SuggestService.Mode.PREFIX);
        }

        int iters = Integer.getInteger("bench.iters", 1000);
        for (int i = 0; i < iters; i++) {
            String q = prefixes.get(rnd.nextInt(prefixes.size()));
            long t0 = System.nanoTime();
            service.suggest(q, 10, SuggestService.Mode.PREFIX);
            long t1 = System.nanoTime();
            nanos.add(t1 - t0);
        }

        report(nanos);
    }

    private void report(List<Long> nanos) {
        nanos.sort(Long::compare);
        long p50 = nanos.get(nanos.size() / 2);
        long p95 = nanos.get((int) Math.floor(nanos.size() * 0.95) - 1);
        long avg = nanos.stream().mapToLong(Long::longValue).sum() / nanos.size();
        System.out.println("count=" + nanos.size() +
                ", avg=" + toMs(avg) + " ms" +
                ", p50=" + toMs(p50) + " ms" +
                ", p95=" + toMs(p95) + " ms");
    }

    private String toMs(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }
}
