package com.app.trashmasters.ManageSensor;


import com.app.trashmasters.ManageSensor.dto.SensorRegistrationRequest;
import com.app.trashmasters.ManageSensor.model.SensorStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SensorService {

    @Autowired
    private SensorRepository sensorRepository;

    // 1. Add New Sensor (Onboarding)
    public Sensor registerSensor(SensorRegistrationRequest request) {
        // 1. Check for duplicates and trigger the 409 Conflict handler
        if (sensorRepository.findBySensorId(request.getSensorId()).isPresent()) {
            throw new DuplicateKeyException("A sensor with ID " + request.getSensorId() + " already exists.");
        }

        Sensor newSensor = new Sensor(request.getSensorId());
//        newSensor.setSensorId(request.getSensorId());
        newSensor.setLastUpdated(Instant.now());
        newSensor.setIsFlagged(false);
        newSensor.setBatteryLevel(100); // Default
        newSensor.setStatus(SensorStatus.INACTIVE); // Default

        // Map the optional fields if provided
        if (request.getBinId() != null) {
            newSensor.setBinId(request.getBinId());
            newSensor.setStatus(SensorStatus.ACTIVE);
        }

        if (request.getBatteryLevel() != null) {
            newSensor.setBatteryLevel(request.getBatteryLevel());
            if (request.getBatteryLevel() < 20) {
                newSensor.setStatus(SensorStatus.LOW_BATTERY);
            }
        }

        if (request.getStatus() != null && newSensor.getStatus() != SensorStatus.LOW_BATTERY) {
            newSensor.setStatus(request.getStatus());
        }

        return sensorRepository.save(newSensor);
    }

    // 2. Delete Sensor (Decommissioning)
    public void deleteSensor(String id) {
        sensorRepository.deleteBySensorId(id);
    }

    // 3. Update Battery (Heartbeat from device)
    public Sensor updateBattery(String id, int level) {
        Sensor sensor = sensorRepository.findBySensorId(id)
                .orElseThrow(() -> new RuntimeException("Sensor not found"));

        sensor.setBatteryLevel(level);
        sensor.setLastUpdated(Instant.now());

        // Auto-flag if battery is critical
        if (level < 20) {
            sensor.setStatus(SensorStatus.LOW_BATTERY);
        } else if (sensor.getStatus() == SensorStatus.LOW_BATTERY) {
            sensor.setStatus(SensorStatus.ACTIVE); // Auto-recover
        }

        return sensorRepository.save(sensor);
    }

    // 4. Flag Sensor (Manual or Automated Issue)
    public Sensor updateStatus(String id, SensorStatus newStatus) {
        Sensor sensor = sensorRepository.findBySensorId(id)
                .orElseThrow(() -> new RuntimeException("Sensor not found"));

        sensor.setStatus(newStatus);
        sensor.setLastUpdated(Instant.now());
        return sensorRepository.save(sensor);
    }

    // 5. Link to Bin
    public Sensor assignToBin(String sensorId, String binId) {
        Sensor sensor = sensorRepository.findBySensorId(sensorId)
                .orElseThrow(() -> new RuntimeException("Sensor not found"));

        sensor.setBinId(binId);
        sensor.setStatus(SensorStatus.ACTIVE);
        return sensorRepository.save(sensor);
    }

    public List<Sensor> getAllSensors() {
        return sensorRepository.findAll();
    }


    public Sensor setFlag(String id, boolean isFlagged, String reason) {
        Sensor sensor = sensorRepository.findBySensorId(id)
                .orElseThrow(() -> new RuntimeException("Sensor not found"));

        sensor.setIsFlagged(isFlagged);

        if (isFlagged) {
            // If flagging, save the reason
            sensor.setFlagReason(reason != null ? reason : "Manual Flag");
            // Optionally set status to MAINTENANCE
            sensor.setStatus(SensorStatus.MALFUNCTION);
        } else {
            // If unflagging, clear the reason and reset status
            sensor.setFlagReason(null);
            sensor.setStatus(SensorStatus.ACTIVE);
        }

        sensor.setLastUpdated(Instant.now());
        return sensorRepository.save(sensor);
    }
}