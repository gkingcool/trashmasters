// src/main/java/com/app/trashmasters/sensor/SensorHistoryController.java
package com.app.trashmasters.ManageSensor;

import com.app.trashmasters.ManageSensor.model.SensorReading;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sensors")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class SensorHistoryController {

    @Autowired
    private SensorReadingRepository historyRepository;

    // GET /api/sensors/history/waste-by-hour
    // Returns waste collected by hour for today
    @GetMapping("/history/waste-by-hour")
    public ResponseEntity<List<Double>> getWasteByHour() {
        List<SensorReading> readings = historyRepository.findAll();

        // Group by hour and sum fill levels
        Map<Integer, Double> wasteByHour = readings.stream()
                .filter(r -> r.getTimestamp() != null)
                .collect(Collectors.groupingBy(
                        r -> Instant.ofEpochMilli(r.getTimestamp().toEpochMilli())
                                .atZone(ZoneId.systemDefault())
                                .getHour(),
                        Collectors.summingDouble(SensorReading::getCalculatedFillLevel)
                ));

        // Return 9 hours (9 AM to 5 PM)
        List<Double> hourlyData = java.util.stream.IntStream.range(9, 18)
                .mapToObj(hour -> wasteByHour.getOrDefault(hour, 0.0))
                .collect(Collectors.toList());

        return ResponseEntity.ok(hourlyData);
    }

    // GET /api/sensors/history/revenue-by-month
    // Returns estimated revenue by month (based on waste collected)
    @GetMapping("/history/revenue-by-month")
    public ResponseEntity<List<Double>> getRevenueByMonth() {
        List<SensorReading> readings = historyRepository.findAll();

        // Group by month and calculate revenue ($0.05 per kg of waste)
        Map<Integer, Double> revenueByMonth = readings.stream()
                .filter(r -> r.getTimestamp() != null)
                .collect(Collectors.groupingBy(
                        r -> Instant.ofEpochMilli(r.getTimestamp().toEpochMilli())
                                .atZone(ZoneId.systemDefault())
                                .getMonthValue(),
                        Collectors.summingDouble(r -> r.getCalculatedFillLevel() * 0.05)
                ));

        // Return 12 months
        List<Double> monthlyRevenue = java.util.stream.IntStream.range(1, 13)
                .mapToObj(month -> revenueByMonth.getOrDefault(month, 0.0))
                .collect(Collectors.toList());

        return ResponseEntity.ok(monthlyRevenue);
    }
}