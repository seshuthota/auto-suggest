package com.example.autosuggest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorMetricsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void metrics_endpoint_available() throws Exception {
        // exercise an endpoint to create a metric
        mvc.perform(get("/suggest").param("q", "micro")).andExpect(status().isOk());
        // metrics endpoints
        mvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics/suggest.query")).andExpect(status().isOk());
    }
}

