package com.example.autosuggest.api;

import com.example.autosuggest.model.Suggestion;
import com.example.autosuggest.model.TrackRequest;
import com.example.autosuggest.service.SuggestService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@RestController
@RequestMapping("/suggest")
@Validated
public class SuggestController {

    private final SuggestService service;
    private final boolean defaultsEnabled;

    public SuggestController(SuggestService service,
                             @Value("${suggest.defaults.enabled:false}") boolean defaultsEnabled) {
        this.service = service;
        this.defaultsEnabled = defaultsEnabled;
    }

    @GetMapping
    public ResponseEntity<List<Suggestion>> suggest(
            @RequestParam("q") @NotBlank @Size(min = 2, max = 100) String q,
            @RequestParam(value = "limit", required = false, defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(value = "mode", required = false, defaultValue = "PREFIX") SuggestService.Mode mode
    ) {
        return ResponseEntity.ok(service.suggest(q, limit, mode));
    }

    @GetMapping("/defaults")
    public ResponseEntity<List<Suggestion>> defaults(
            @RequestParam(value = "limit", required = false, defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        if (!defaultsEnabled) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(service.defaultSuggestions(limit));
    }

    @PostMapping("/track")
    public ResponseEntity<Void> track(@RequestBody TrackRequest req) {
        service.trackSelection(req.id(), req.value());
        return ResponseEntity.accepted().build();
    }
}
