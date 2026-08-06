package com.project.wastepilot.controller;

import com.project.wastepilot.domain.dto.operations.InventoryLogResponse;
import com.project.wastepilot.domain.dto.sensor.SensorIngestRequest;
import com.project.wastepilot.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryLogResponse ingest(@Valid @RequestBody SensorIngestRequest request) {
        return sensorService.ingest(request);
    }
}
