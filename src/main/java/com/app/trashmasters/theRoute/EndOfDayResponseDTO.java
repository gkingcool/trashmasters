package com.app.trashmasters.theRoute;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Summary of what was processed during end-of-day close-out")
public class EndOfDayResponseDTO {

    @Schema(example = "2", description = "Number of routes successfully closed")
    private int routesClosed;

    @Schema(description = "One line per truck showing its updated load after the shift")
    private List<TruckSummary> truckUpdates;

    @Schema(example = "2", description = "Number of bins that received an overdue penalty")
    private int binsMarkedOverdue;

    @Schema(description = "Bin IDs that were penalized (for confirmation)")
    private List<String> overdueAppliedTo;

    @Schema(example = "8", description = "Number of bins whose fill level was reset to 0 after collection")
    private int binsReset;

    @Schema(description = "Bin IDs that were reset to 0% fill (for confirmation)")
    private List<String> binsResetIds;

    @Schema(example = "Database ready for tomorrow.")
    private String message;

    @Data
    public static class TruckSummary {
        @Schema(example = "TRK-001")
        private String truckId;
        @Schema(example = "DRV-001")
        private String driverId;
        @Schema(example = "5.2", description = "Compacted yards now stored on the truck document")
        private double updatedLoadYards;

        public TruckSummary(String truckId, String driverId, double updatedLoadYards) {
            this.truckId = truckId;
            this.driverId = driverId;
            this.updatedLoadYards = updatedLoadYards;
        }
    }
}

