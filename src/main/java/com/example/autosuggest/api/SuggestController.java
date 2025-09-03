package com.example.autosuggest.api;

import com.example.autosuggest.model.Suggestion;
import com.example.autosuggest.service.SuggestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/suggest")
public class SuggestController {

    private final SuggestService service;

    public SuggestController(SuggestService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Suggestion>> suggest(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit,
            @RequestParam(value = "mode", required = false, defaultValue = "PREFIX") SuggestService.Mode mode
    ) {
        return ResponseEntity.ok(service.suggest(q, limit, mode));
    }
}

