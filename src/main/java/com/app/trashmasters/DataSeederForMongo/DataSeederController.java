package com.app.trashmasters.DataSeederForMongo;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
@Tag(name = "Dev Tools", description = "Development utilities — database seeding")
public class DataSeederController {

    @Autowired
    private DataSeederService dataSeederService;

    @Autowired
    private DataFixService dataFixService;

    // Endpoint: POST /api/dev/seed
    @Operation(summary = "Seed database", description = "Populates MongoDB with sample bins, trucks, employees, and sensors")
    @PostMapping("/seed")
    public ResponseEntity<String> seedData() {
        String result = dataSeederService.seedDatabase();
        return ResponseEntity.ok(result);
    }

    // Endpoint: POST /api/dev/fix-locations-and-sensors
    @Operation(
        summary = "Fix bin locations & create sensors",
        description = "One-time migration: reassigns all bin coordinates to tight Bellevue clusters " +
                      "(10 North, 10 South, rest Downtown) and populates the sensors collection with " +
                      "IOT-001…IOT-NNN linked to each bin."
    )
    @PostMapping("/fix-locations-and-sensors")
    public ResponseEntity<String> fixLocationsAndSensors() {
        String result = dataFixService.fixData();
        return ResponseEntity.ok(result);
    }

    // Endpoint: POST /api/dev/boost-fill-levels
    @Operation(
        summary = "Boost bin fill levels",
        description = "Sets fill levels so ~90% of bins are ≥75% full. " +
                      "Distribution: 20% critical (88–100%), 70% high (75–87%), 10% normal (30–74%). " +
                      "Also updates bin status (CRITICAL/FULL/NORMAL) accordingly."
    )
    @PostMapping("/boost-fill-levels")
    public ResponseEntity<String> boostFillLevels() {
        String result = dataFixService.boostFillLevels();
        return ResponseEntity.ok(result);
    }

    // Endpoint: POST /api/dev/update-bin-coordinates
    @Operation(
        summary = "Update bin coordinates",
        description = "One-time migration: sets precise lat/lon for BEL-BIN-001 through BEL-BIN-070 " +
                      "using the provided coordinate list."
    )
    @PostMapping("/update-bin-coordinates")
    public ResponseEntity<String> updateBinCoordinates() {
        String result = dataFixService.updateBinCoordinates();
        return ResponseEntity.ok(result);
    }

    // Endpoint: POST /api/dev/reset-truck-loads
    @Operation(
        summary = "Reset all truck loads to 0",
        description = "Sets currentCompactedYards=0 for all trucks. " +
                      "Use this at the start of a new day if end-of-day was not called. " +
                      "Route generation also auto-resets carry-over loads, but this endpoint lets you do it manually."
    )
    @PostMapping("/reset-truck-loads")
    public ResponseEntity<String> resetTruckLoads() {
        return ResponseEntity.ok(dataFixService.resetTruckLoads());
    }
}