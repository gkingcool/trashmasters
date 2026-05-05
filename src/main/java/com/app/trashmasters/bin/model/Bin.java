package com.app.trashmasters.bin.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bins")
public class Bin {
    @Id
    @Schema(example = "69aca60f49850123b513137b")
    private String id;

    @Indexed(unique = true)
    @Schema(example = "BEL-BIN-023")
    private String binId;

    @Schema(example = "Bellevue Park - North")
    private String locationName;

    private Location location;

    @Schema(example = "45.0", description = "Fill percent 0–100")
    private Double fillLevel;

    @Schema(example = "2026-03-07T22:26:23.548Z")
    private Instant lastUpdated;

    @Schema(example = "120")
    private int depthCm;

    @Schema(example = "SENSOR-X99")
    private String sensorId;

    @Schema(example = "NORMAL")
    private BinStatus status;

    private Map<Integer, Double> futurePredictions;

    @Schema(example = "2026-03-08T20:26:09.970Z")
    private Instant lastPredicted;

    @Schema(example = "0")
    private Integer daysOverdue = 0;

    @Schema(example = "8")
    private Integer capacityYards;

    @Schema(example = "PUBLIC")
    private BinZone zone = BinZone.PUBLIC;

    @Schema(example = "false")
    private boolean isFlagged;

    @Schema(example = "Lid broken")
    private String issue;

    public Double getLatitude() {
        return location != null ? location.getLat() : null;
    }

    public Double getLongitude() {
        return location != null ? location.getLon() : null;
    }
}