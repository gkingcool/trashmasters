package com.app.trashmasters.ManageSensor;


import com.app.trashmasters.bin.BinRepository;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinStatus;
import com.app.trashmasters.ManageSensor.dto.SensorDataRequest;
import com.app.trashmasters.ManageSensor.model.SensorReading;
import com.app.trashmasters.ManageSensor.model.SensorStatus;
import com.app.trashmasters.notification.NotificationService;
import com.app.trashmasters.notification.model.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
public class SensorIngestionService {

    @Autowired private SensorRepository sensorRepository;
    @Autowired private BinRepository binRepository;
    @Autowired private SensorReadingRepository historyRepository;
    @Autowired private NotificationService notificationService;

    // The Main Pipeline Method
    @Transactional
    public void processSensorData(SensorDataRequest request) {

        // 1. Validate Sensor & Update Battery
        Sensor sensor = sensorRepository.findBySensorId(request.getSensorId())
                .orElseThrow(() -> new RuntimeException("Unknown Sensor ID: " + request.getSensorId()));

        boolean wasLow = sensor.getBatteryLevel() != null && sensor.getBatteryLevel() < 20;
        sensor.setBatteryLevel(request.getBattery());
        sensor.setLastUpdated(Instant.now());

        // ✅ ALERT: If battery drops below 15%, send notification to Admin
        if (request.getBattery() < 15 && !wasLow) {
            sensor.setStatus(SensorStatus.LOW_BATTERY);
            notificationService.createSensorNotification(
                    sensor.getSensorId(),
                    "🔋 Low Battery Alert",
                    "Sensor " + sensor.getSensorId() + " battery is low (" + request.getBattery() + "%).",
                    NotificationType.WARNING
            );
        } else if (request.getBattery() >= 20 && sensor.getStatus() == SensorStatus.LOW_BATTERY) {
            sensor.setStatus(SensorStatus.ACTIVE);
        }

        sensorRepository.save(sensor);

        // 2. Stop if Sensor isn't inside a Bin
        if (sensor.getBinId() == null) {
            System.out.println("Sensor " + request.getSensorId() + " has no Bin assigned. Skipping calculation.");
            return;
        }

        // 3. Fetch Bin Using the Custom Business ID
        Bin bin = binRepository.findByBinId(sensor.getBinId())
                .orElseThrow(() -> new RuntimeException("Bin not found with custom ID: " + sensor.getBinId()));

        // 4. Calculate Fullness
        double depth = bin.getDepthCm();
        double rawDistance = request.getDistanceCm();

        double fillPercent = ((depth - rawDistance) / depth) * 100.0;
        fillPercent = Math.max(0, Math.min(100, fillPercent)); // Clamp 0-100

        // 5. Update Bin "Live State"
        bin.setFillLevel(fillPercent);
        bin.setLastUpdated(Instant.now());

        // Auto-Status Logic
        if (fillPercent >= 90) bin.setStatus(BinStatus.CRITICAL);
        else if (fillPercent >= 70) bin.setStatus(BinStatus.FULL);
        else bin.setStatus(BinStatus.NORMAL);

        binRepository.save(bin); // MongoDB knows to update the existing doc because it has the hidden _id

        // 6. Create Enriched History (For SageMaker)
        saveToHistory(sensor, bin, request, fillPercent);
    }

    private void saveToHistory(Sensor sensor, Bin bin, SensorDataRequest request, double fillPercent) {
        SensorReading reading = new SensorReading();

        reading.setSensorId(sensor.getSensorId());
        reading.setBinId(bin.getBinId()); // Save the clean custom ID to history, not the Mongo ID
        reading.setRawDistance(request.getDistanceCm());
        reading.setCalculatedFillLevel(fillPercent);
        reading.setBatteryLevel(request.getBattery());
        reading.setTimestamp(Instant.now());

        // Enrichment
        LocalDateTime now = LocalDateTime.now();
        reading.setDayOfWeek(now.getDayOfWeek().getValue());
        reading.setHourOfDay(now.getHour());
        reading.setIsWeekend(now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY);
        reading.setTemperatureF(mockTemperature(now.getMonthValue()));
        reading.setIsHoliday(false);

        historyRepository.save(reading);
    }

    private Double mockTemperature(int month) {
        if (month >= 5 && month <= 9) return 85.0 + (Math.random() * 10);
        return 55.0 + (Math.random() * 10);
    }
}