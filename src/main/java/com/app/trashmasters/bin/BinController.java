package com.app.trashmasters.bin;

import com.app.trashmasters.bin.dto.BinCreateRequest;
import com.app.trashmasters.bin.dto.BinFlagRequest;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinZone;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bins")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "Bins", description = "Waste bin CRUD, filtering by zone/status, and flagging")
public class BinController {

    private final BinService binService;
    private final BinRepository binRepository;

    @Autowired
    public BinController(BinService binService, BinRepository binRepository) {
        this.binService = binService;
        this.binRepository = binRepository;
    }

    // GET all bins
    @Operation(summary = "Get all bins")
    @GetMapping
    public ResponseEntity<List<Bin>> getAllBins() {
        return ResponseEntity.ok(binService.getAllBins());
    }

    // GET single bin by binId
    @Operation(summary = "Get bin by ID")
    @GetMapping("/{binId}")
    public ResponseEntity<?> getBinById(@PathVariable String binId) {
        try {
            return ResponseEntity.ok(binService.getBinByBinId(binId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // POST - Create a new Bin
    @Operation(summary = "Create a new bin")
    @PostMapping("/createBin")
    public ResponseEntity<Bin> createBin(@RequestBody BinCreateRequest request) {
        // Basic validation: Make sure they actually sent an ID and Depth
        if (request.getBinId() == null || request.getDepthCm() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Bin savedBin = binService.createBin(request);
            return new ResponseEntity<>(savedBin, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    // PUT - Flag an Issue
    @Operation(summary = "Flag or unflag a bin issue")
    @PutMapping("/{id}/flag")
    public ResponseEntity<Bin> flagBinIssue(
            @PathVariable String id,
            @RequestBody BinFlagRequest request) {

        return ResponseEntity.ok(binService.setFlag(id, request.isFlagged(), request.getIssue()));
    }

    // GET - Get only full bins (above threshold)
    @Operation(summary = "Get full bins", description = "Returns bins above the fill-level threshold (default 70%)")
    @GetMapping("/full")
    public ResponseEntity<List<Bin>> getFullBins(
            @RequestParam(defaultValue = "70") int threshold) {
        return ResponseEntity.ok(binService.getFullBins(threshold));
    }

    // GET - Get bins by zone (COMMERCIAL or PUBLIC)
    @Operation(summary = "Get bins by zone", description = "Filter by COMMERCIAL or PUBLIC")
    @GetMapping("/zone/{zone}")
    public ResponseEntity<?> getBinsByZone(@PathVariable String zone) {
        try {
            BinZone binZone = BinZone.valueOf(zone.toUpperCase());
            return ResponseEntity.ok(binRepository.findByZone(binZone));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid zone. Use COMMERCIAL or PUBLIC.");
        }
    }

    // GET - Get all flagged/maintenance bins
    @Operation(summary = "Get all flagged bins")
    @GetMapping("/flagged")
    public ResponseEntity<List<Bin>> getFlaggedBins() {
        return ResponseEntity.ok(binRepository.findByIsFlaggedTrue());
    }

    // GET - Get all overdue bins (skipped from previous days)
    @Operation(summary = "Get overdue bins", description = "Bins skipped from previous days (daysOverdue > 0)")
    @GetMapping("/overdue")
    public ResponseEntity<List<Bin>> getOverdueBins() {
        return ResponseEntity.ok(binRepository.findByDaysOverdueGreaterThan(0));
    }

    // DELETE /api/bins/{id}
    @Operation(summary = "Delete a bin")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBin(@PathVariable String id) {

        try {
            binService.deleteBin(id);
            return ResponseEntity.ok("Bin " + id + " deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error deleting bin.");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Bin> updateBin(
            @PathVariable String id,
            @RequestBody BinCreateRequest request) {
        try {
            Bin updatedBin = binService.updateBin(id, request);
            return ResponseEntity.ok(updatedBin);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

}
