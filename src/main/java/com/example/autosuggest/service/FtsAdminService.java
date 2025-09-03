package com.example.autosuggest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FtsAdminService {

    private final JdbcTemplate jdbc;
    private final boolean manageFts;

    public FtsAdminService(NamedParameterJdbcTemplate npJdbc,
                           @Value("${suggest.fts.manage:true}") boolean manageFts) {
        this.jdbc = npJdbc.getJdbcTemplate();
        this.manageFts = manageFts;
    }

    public void ensureTriggers() {
        if (!manageFts) return;
        // Create external-content sync triggers (idempotent)
        jdbc.execute("CREATE TRIGGER IF NOT EXISTS people_ai AFTER INSERT ON people BEGIN\n" +
                "  INSERT INTO people_fts(rowid, name) VALUES (new.id, new.name);\n" +
                "END;");
        jdbc.execute("CREATE TRIGGER IF NOT EXISTS people_ad AFTER DELETE ON people BEGIN\n" +
                "  INSERT INTO people_fts(people_fts, rowid, name) VALUES('delete', old.id, old.name);\n" +
                "END;");
        jdbc.execute("CREATE TRIGGER IF NOT EXISTS people_au AFTER UPDATE ON people BEGIN\n" +
                "  INSERT INTO people_fts(people_fts, rowid, name) VALUES('delete', old.id, old.name);\n" +
                "  INSERT INTO people_fts(rowid, name) VALUES (new.id, new.name);\n" +
                "END;");
    }

    public void rebuild() {
        if (!manageFts) return;
        jdbc.execute("INSERT INTO people_fts(people_fts) VALUES('rebuild')");
    }

    public void optimize() {
        if (!manageFts) return;
        jdbc.execute("INSERT INTO people_fts(people_fts) VALUES('optimize')");
    }
}

