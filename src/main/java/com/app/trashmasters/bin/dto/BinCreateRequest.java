package com.app.trashmasters.bin.dto;

import com.app.trashmasters.bin.model.BinZone;
import com.app.trashmasters.bin.model.Location;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Request body for creating a new waste bin")
public class BinCreateRequest {

    @Schema(example = "BIN-101")
    private String binId;
    @Schema(example = "Bellevue Park - North")
    private String locationName;
    private Location location;
    @Schema(example = "120", description = "Internal depth of the bin in centimeters")
    private Integer depthCm;
    @Schema(example = "0.0", description = "Current fill level percentage (0-100)")
    private Double fillLevel;
    @Schema(example = "ESP32-X1")
    private String sensorId;
    @Schema(example = "6", description = "Bin capacity in cubic yards (4, 6, or 8)")
    private Integer capacityYards;
    @Schema(example = "COMMERCIAL", description = "COMMERCIAL or PUBLIC")
    private BinZone zone;
    @Schema(example = "NORMAL", description = "NORMAL, FULL, CRITICAL, or MAINTENANCE")
    private String status;
    // ✅ Flat fields to match frontend payload
    private Double latitude;
    private Double longitude;

    // Optional prediction seeding
    private Integer predictedFillLevel;
    private LocalDateTime predictionTargetTime;
}