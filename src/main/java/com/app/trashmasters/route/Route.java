package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "routes")
public class Route {
    @Id
    @Schema(example = "69bcf10a22880000ab000001")
    private String id;

    @Schema(example = "DRV-003")
    private String driverId;
    @Schema(example = "TRK-002")
    private String truckId;

    @Schema(example = "2026-04-19", description = "The date this route is scheduled for")
    private LocalDate routeDate;

    @Schema(example = "[\"BEL-BIN-023\", \"BEL-BIN-007\", \"BEL-BIN-015\"]")
    private List<String> binIds;

    @Schema(example = "CREATED", description = "CREATED, IN_PROGRESS, or COMPLETED")
    private String status;

    @Schema(example = "2026-04-19T07:00:00")
    private LocalDateTime createdAt;

    @Schema(example = "12")
    private int totalStops;

    @Schema(example = "[{\"lat\":47.61,\"lon\":-122.2,\"type\":\"STATION\",...}]")
    private List<RouteStepDTO> steps;

    @Schema(example = "185")
    private long totalTimeMinutes;

    @Schema(example = "0.0")
    private double startingTruckLoadYards;

    @Schema(example = "12.5")
    private double endingTruckVolumeYards;
}