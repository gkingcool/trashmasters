package com.app.trashmasters.theRoute;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "End-of-day shift report submitted after all drivers finish")
public class EndOfDayRequestDTO {

    @ArraySchema(schema = @Schema(implementation = CompletedRouteDTO.class))
    @Schema(description = "One entry per truck/driver that completed a route today")
    private List<CompletedRouteDTO> completedRoutes;

    @ArraySchema(schema = @Schema(example = "BEL-BIN-014"))
    @Schema(description = "Bin IDs that were skipped today — they receive an overdue penalty for tomorrow")
    private List<String> skippedBinIds;
}
