package com.app.trashmasters.Truck;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "trucks")
public class Truck {
    @Id
    @Schema(example = "69aca1832288bd342fd48a3d")
    private String id;

    @Indexed(unique = true)
    @Schema(example = "TRK-002")
    private String truckId;
    @Schema(example = "DRV-002")
    private String assignedDriverId;

    @Schema(example = "30.0", description = "Maximum compacted cubic yards this truck can hold")
    private Double maxCapacityYards = 30.0;

    @Schema(example = "0.0")
    private Double currentCompactedYards = 0.0;
}

