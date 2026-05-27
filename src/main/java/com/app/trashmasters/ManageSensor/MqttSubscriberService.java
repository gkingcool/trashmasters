package com.app.trashmasters.ManageSensor;

import com.app.trashmasters.ManageSensor.dto.SensorDataRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes to the HiveMQ MQTT broker over SSL using the HiveMQ MQTT Client library.
 * TLS is handled automatically — no manual SSLContext setup required.
 *
 * <p>Expected topic pattern: {@code waste/sensor/{sensorId}/distance}</p>
 *
 * <p>Supported payload formats (battery defaults to 100 when not provided):
 * <ul>
 *   <li>Plain number:      {@code 13.3}</li>
 *   <li>Comma-separated:  {@code 13.3,88}  (distance,battery)</li>
 *   <li>JSON object:      {@code {"distance":13.3,"battery":88}}</li>
 * </ul>
 */
@Service
public class MqttSubscriberService {

    // Credentials — stored as fields to avoid .properties file issues with special chars (# breaks parsing)
    private static final String MQTT_HOST = "";
    private static final int    MQTT_PORT = 8883;
    private static final String MQTT_USER = "";
    private static final String MQTT_PASS = "";

    @Value("${mqtt.topic:waste/sensor/distance}")
    private String topic;

    @Autowired
    private SensorIngestionService ingestionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_BATTERY = 100;

    private Mqtt3AsyncClient mqttClient;

    @PostConstruct
    public void connect() {
        System.out.println("🔍 MQTT CONFIG:");
        System.out.println("   host     : " + MQTT_HOST);
        System.out.println("   port     : " + MQTT_PORT);
        System.out.println("   username : " + MQTT_USER);
        System.out.println("   password length: " + MQTT_PASS.length());
        System.out.println("   topic    : " + topic);

        mqttClient = Mqtt3Client.builder()
                .identifier("trashmasters-" + System.currentTimeMillis())
                .serverHost(MQTT_HOST)
                .serverPort(MQTT_PORT)
                .sslWithDefaultConfig()
                .buildAsync();

        System.out.println("🔌 MQTT attempting to connect...");

        mqttClient.connectWith()
                .simpleAuth()
                .username(MQTT_USER)
                .password(MQTT_PASS.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth()
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        System.err.println("❌ MQTT connection FAILED: " + throwable.getMessage());
                        System.err.println("   Caused by: " + (throwable.getCause() != null ? throwable.getCause().getMessage() : "n/a"));
                    } else {
                        System.out.println("✅ MQTT connected! Return code: " + connAck.getReturnCode());
                        subscribeToTopic();
                    }
                });
    }

    private void subscribeToTopic() {
        mqttClient.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        System.err.println("❌ MQTT subscribe FAILED: " + throwable.getMessage());
                    } else {
                        System.out.println("✅ MQTT subscribed to: " + topic);
                    }
                });

        // Register global message handler
        mqttClient.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            String incomingTopic = publish.getTopic().toString();
            if (publish.getPayload().isEmpty()) {
                System.err.println("⚠️  MQTT: empty payload on topic: " + incomingTopic);
                return;
            }
            String payload = StandardCharsets.UTF_8.decode(publish.getPayload().get()).toString().trim();
            handleMessage(incomingTopic, payload);
        });
    }

    private void handleMessage(String incomingTopic, String payload) {
        try {
            String[] parts = incomingTopic.split("/");
            String sensorId;
            if (parts.length >= 4) {
                sensorId = parts[2];
            } else {
                System.err.println("⚠️  MQTT: no sensorId in topic '" + incomingTopic +
                        "'. Publish to waste/sensor/{sensorId}/distance instead.");
                return;
            }

            double distanceCm;
            int battery = DEFAULT_BATTERY;

            if (payload.startsWith("{")) {
                // JSON: {"distance":13.3,"battery":88}
                JsonNode node = objectMapper.readTree(payload);
                distanceCm = node.get("distance").asDouble();
                if (node.has("battery") && !node.get("battery").isNull()) {
                    battery = node.get("battery").asInt(DEFAULT_BATTERY);
                }
            } else if (payload.contains(",")) {
                // CSV: "13.3,88"
                String[] tokens = payload.split(",", 2);
                distanceCm = Double.parseDouble(tokens[0].trim());
                if (tokens.length > 1 && !tokens[1].trim().isEmpty()) {
                    battery = Integer.parseInt(tokens[1].trim());
                }
            } else {
                // Plain number: "13.3"
                distanceCm = Double.parseDouble(payload);
            }

            System.out.printf("📡 MQTT | sensor=%s | distance=%.1f cm | battery=%d%%%n",
                    sensorId, distanceCm, battery);

            SensorDataRequest request = new SensorDataRequest();
            request.setSensorId(sensorId);
            request.setDistanceCm(distanceCm);
            request.setBattery(battery);

            ingestionService.processSensorDataFromMqtt(request);

        } catch (NumberFormatException e) {
            System.err.println("⚠️  MQTT bad payload on topic '" + incomingTopic + "': " + payload);
        } catch (Exception e) {
            System.err.println("❌ MQTT message processing error: " + e.getMessage());
        }
    }

    @PreDestroy
    public void disconnect() {
        if (mqttClient != null) {
            mqttClient.disconnect()
                    .whenComplete((v, t) -> System.out.println("🔌 MQTT disconnected gracefully."));
        }
    }
}
