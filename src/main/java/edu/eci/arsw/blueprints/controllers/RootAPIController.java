package edu.eci.arsw.blueprints.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootAPIController {

    @GetMapping("/")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> root() {
        Map<String, Object> data = Map.of(
                "service", "Blueprints API",
                "version", "v1",
                "endpoints", Map.of(
                        "blueprints", "/api/v1/blueprints",
                        "swagger", "/swagger-ui.html",
                        "openapi", "/v3/api-docs"
                )
        );

        return ResponseEntity.ok(
                new ApiResponseDto<>(HttpStatus.OK.value(), "ok", data)
        );
    }
}
