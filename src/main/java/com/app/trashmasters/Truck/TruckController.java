package com.app.trashmasters.Truck;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "Trucks", description = "Fleet management — CRUD operations for collection trucks")
public class TruckController {

    @Autowired
    private TruckService truckService;

    @Autowired
    private TruckRepository truckRepository;

    // POST /api/trucks
    @Operation(summary = "Create a truck")
    @PostMapping
    public ResponseEntity<?> createTruck(@RequestBody TruckRequest request) {
        try {
            Truck truck = new Truck();
            truck.setTruckId(request.getTruckId());
            truck.setAssignedDriverId(request.getAssignedDriverId());
            truck.setMaxCapacityYards(
                    request.getMaxCapacityYards() != null ? request.getMaxCapacityYards() : 30.0);
            truck.setCurrentCompactedYards(
                    request.getCurrentCompactedYards() != null ? request.getCurrentCompactedYards() : 0.0);

            Truck savedTruck = truckService.createTruck(truck);
            return new ResponseEntity<>(savedTruck, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error creating truck.");
        }
    }

    // GET /api/trucks
    @Operation(summary = "Get all trucks")
    @GetMapping
    public ResponseEntity<List<Truck>> getAllTrucks() {
        try {
            List<Truck> trucks = truckService.getAllTrucks();
            return ResponseEntity.ok(trucks);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // GET /api/trucks/{truckId}
    @Operation(summary = "Get truck by ID")
    @GetMapping("/{truckId}")
    public ResponseEntity<?> getTruckById(@PathVariable String truckId) {
        try {
            Truck truck = truckRepository.findByTruckId(truckId)
                    .orElseThrow(() -> new RuntimeException("Truck not found: " + truckId));
            return ResponseEntity.ok(truck);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // PUT /api/trucks/{truckId}
    @Operation(summary = "Update a truck")
    @PutMapping("/{truckId}")
    public ResponseEntity<?> updateTruck(@PathVariable String truckId, @RequestBody TruckRequest request) {
        try {
            Truck truckData = new Truck();
            truckData.setAssignedDriverId(request.getAssignedDriverId());
            if (request.getMaxCapacityYards() != null) {
                truckData.setMaxCapacityYards(request.getMaxCapacityYards());
            }
            truckData.setCurrentCompactedYards(request.getCurrentCompactedYards());

            Truck updatedTruck = truckService.updateTruck(truckId, truckData);
            return ResponseEntity.ok(updatedTruck);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error updating truck.");
        }
    }

    // DELETE /api/trucks/{truckId}
    @Operation(summary = "Delete a truck")
    @DeleteMapping("/{truckId}")
    public ResponseEntity<?> deleteTruck(@PathVariable String truckId) {
        try {
            truckService.deleteTruck(truckId);
            return ResponseEntity.ok("Truck " + truckId + " deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error deleting truck.");
        }
    }
}