package kg.post.courier.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * POST.KG — Серверная часть курьерской службы доставки госномеров (Java 21, Spring Boot 3)
 *
 * Ключевые модули:
 * 1. API-шлюз приёма данных клиентов и номерных знаков из внешней системы (Аю Гранд / ГРС):
 *    POST /api/v1/integration/incoming-orders
 * 2. Хранение и управление полными данными каждого курьера (профиль, авто, device_id, смены, GPS, рейс):
 *    GET/POST/PUT /api/v1/couriers, POST /api/v1/couriers/{id}/telemetry
 * 3. Управление передачей заказов при поломке/SOS с контролем ответственности по QR:
 *    POST /api/v1/transfers, POST /api/v1/transfers/{id}/confirm-qr
 */
@SpringBootApplication
@RestController
@RequestMapping("/api/v1")
public class PostKgSpringBackend {

    public static void main(String[] args) {
        SpringApplication.run(PostKgSpringBackend.class, args);
    }

    // In-memory / Repository mirror for demonstration alongside PostgreSQL + PostGIS
    private final Map<String, CourierRecord> courierStorage = new ConcurrentHashMap<>();
    private final Map<String, OrderRecord> orderStorage = new ConcurrentHashMap<>();

    // ========================================================================
    // 1. ВНЕШНИЙ API: ПОЛУЧЕНИЕ ДАННЫХ КЛИЕНТА И ГОСНОМЕРОВ ДЛЯ ДОСТАВКИ
    // ========================================================================

    public record IncomingClientPlatePayload(
            String externalOrderId,
            String recipientName,
            String recipientPhone,
            String recipientPin,
            String deliveryAddress,
            String district,
            double deliveryLat,
            double deliveryLon,
            String carPlate,          // Госномер, который доставляем этому клиенту (напр. "01 KG 777 AAA")
            String itemType,          // "SET" (комплект 2 шт.) или "SINGLE" (одиночный 1 шт.)
            String packageQrCode,     // QR/штрих-код упаковки в Аю Гранд
            String timeWindow,
            String priority,
            String comment
    ) {}

    @PostMapping("/integration/incoming-orders")
    public ResponseEntity<Map<String, Object>> receiveClientAndPlateFromApi(
            @RequestBody List<IncomingClientPlatePayload> incomingBatch
    ) {
        List<OrderRecord> createdOrders = new ArrayList<>();
        for (IncomingClientPlatePayload item : incomingBatch) {
            String orderNumber = "PKG-" + (1001 + orderStorage.size());
            OrderRecord order = new OrderRecord(
                    UUID.randomUUID().toString(),
                    orderNumber,
                    item.externalOrderId(),
                    item.recipientName(),
                    item.recipientPhone(),
                    item.recipientPin(),
                    item.deliveryAddress(),
                    item.district(),
                    item.deliveryLat(),
                    item.deliveryLon(),
                    item.carPlate(),
                    item.itemType() != null ? item.itemType() : "SET",
                    "SET".equalsIgnoreCase(item.itemType()) ? 2 : 1,
                    item.packageQrCode() != null ? item.packageQrCode() : "QR-" + orderNumber,
                    "Аю Гранд (г. Бишкек, ул. Чокана Валиханова 2)",
                    42.8542,
                    74.6685,
                    item.timeWindow() != null ? item.timeWindow() : "09:00 - 18:00",
                    item.priority() != null ? item.priority() : "NORMAL",
                    item.comment(),
                    "NEW",
                    null,
                    Instant.now().toString()
            );
            orderStorage.put(order.number(), order);
            createdOrders.add(order);
        }
        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "receivedCount", createdOrders.size(),
                "orders", createdOrders,
                "timestampUtc", Instant.now().toString()
        ));
    }

    // ========================================================================
    // 2. ХРАНЕНИЕ И УПРАВЛЕНИЕ ДАННЫМИ КАЖДОГО КУРЬЕРА
    // ========================================================================

    public record CourierRecord(
            String id,
            String fullName,
            String phone,
            String photoUrl,
            String vehicleMake,
            String vehicleColor,
            String vehiclePlate,
            String deviceId,
            String deviceModel,
            String accountStatus,     // ACTIVE / BLOCKED
            String shiftStatus,       // MOVING / AT_POINT / IDLE / PROBLEM_SOS / OFFLINE
            double lat,
            double lon,
            double speedKmh,
            int batteryLevel,
            String networkType,
            int totalDelivered,
            int totalNotDelivered,
            double shiftDistanceKm,
            List<String> assignedOrderNumbers,
            String updatedAtUtc
    ) {}

    public record OrderRecord(
            String id,
            String number,
            String externalApiId,
            String recipientName,
            String recipientPhone,
            String recipientPin,
            String address,
            String district,
            double lat,
            double lon,
            String carPlate,
            String itemType,
            int itemQty,
            String packageQrCode,
            String pickupPointName,
            double pickupLat,
            double pickupLon,
            String timeWindow,
            String priority,
            String comment,
            String status,
            String courierId,
            String createdAtUtc
    ) {}

    @GetMapping("/couriers")
    public Collection<CourierRecord> getAllCouriers() {
        return courierStorage.values();
    }

    @PostMapping("/couriers")
    public ResponseEntity<CourierRecord> saveOrUpdateCourier(@RequestBody CourierRecord courier) {
        courierStorage.put(courier.id(), courier);
        return ResponseEntity.ok(courier);
    }

    @GetMapping("/couriers/{courierId}")
    public ResponseEntity<CourierRecord> getCourierById(@PathVariable String courierId) {
        CourierRecord record = courierStorage.get(courierId);
        return record != null ? ResponseEntity.ok(record) : ResponseEntity.notFound().build();
    }
}

