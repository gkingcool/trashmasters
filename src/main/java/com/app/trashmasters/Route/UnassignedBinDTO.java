package com.app.trashmasters.Route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "A bin that was eligible for pickup but couldn't fit into any route")
public class UnassignedBinDTO {
    @Schema(example = "BEL-BIN-027")
    private String binId;
    @Schema(example = "68.0")
    private double fillLevel;
    @Schema(example = "85.0", description = "Highest predicted fill % (4h/8h/12h)")
    private double predictedFillLevel;
    @Schema(example = "1")
    private int daysOverdue;
    @Schema(example = "6")
    private int capacityYards;
    @Schema(example = "COMMERCIAL")
    private String zone;
    @Schema(example = "URGENT: ≥80% full", description = "Why this bin needs attention")
    private String reason;

    public UnassignedBinDTO(String binId, double fillLevel, double predictedFillLevel,
                            int daysOverdue, int capacityYards, String zone, String reason) {
        this.binId = binId;
        this.fillLevel = fillLevel;
        this.predictedFillLevel = predictedFillLevel;
        this.daysOverdue = daysOverdue;
        this.capacityYards = capacityYards;
        this.zone = zone;
        this.reason = reason;
    }
}

