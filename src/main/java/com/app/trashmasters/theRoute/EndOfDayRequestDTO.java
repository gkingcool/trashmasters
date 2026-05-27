package com.app.trashmasters.theRoute;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "End-of-day shift report. Updates truck loads and penalizes skipped bins. " +
        "Bin fill levels are NOT cleared here — use POST /api/routes/{routeId}/pickup/{binId} " +
        "in real-time during the route to confirm each bin collection.")
public class EndOfDayRequestDTO {

    @ArraySchema(schema = @Schema(implementation = CompletedRouteDTO.class))
    @Schema(description = "One entry per truck/driver that completed a route today")
    private List<CompletedRouteDTO> completedRoutes;

    @ArraySchema(schema = @Schema(example = "BEL-BIN-014"))
    @Schema(description = "Bin IDs that were skipped today — they receive an overdue penalty for tomorrow")
    private List<String> skippedBinIds;
}
