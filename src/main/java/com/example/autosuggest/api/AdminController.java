package com.example.autosuggest.api;

import com.example.autosuggest.service.FtsAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/fts")
public class AdminController {

    private final FtsAdminService fts;

    public AdminController(FtsAdminService fts) {
        this.fts = fts;
    }

    @PostMapping("/ensure-triggers")
    public ResponseEntity<Void> ensureTriggers() {
        fts.ensureTriggers();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Void> rebuild() {
        fts.rebuild();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/optimize")
    public ResponseEntity<Void> optimize() {
        fts.optimize();
        return ResponseEntity.accepted().build();
    }
}

