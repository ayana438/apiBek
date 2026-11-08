package kg.post.courier.controller;

import kg.post.courier.entity.*;
import kg.post.courier.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Основной REST API контроллер системы курьерской службы POST.KG (PostgreSQL):
 * 1. Приём данных клиентов и госномеров из внешней системы (Аю Гранд / ГРС) по API:
 *    POST /api/v1/integration/incoming-orders
 * 2. Персональный API синхронизации каждого аккаунта курьера (отдельные данные, свой GPS, отправка фотоотчёта доставки):
 *    GET  /api/v1/courier-app/{courierId}/my-account
 *    POST /api/v1/courier-app/{courierId}/gps
 *    POST /api/v1/courier-app/{courierId}/orders/{orderId}/deliver-photo-report
 * 3. Хранение и управление полными данными каждого курьера и заказов в админке:
 *    GET/POST /api/v1/couriers, GET/POST /api/v1/orders, POST /api/v1/transfers
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class PostKgApiController {

    private final CourierRepository courierRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final TransferRepository transferRepository;

    private static final DateTimeFormatter BISHKEK_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Bishkek"));

    public PostKgApiController(
            CourierRepository courierRepository,
            OrderRepository orderRepository,
            OrderStatusHistoryRepository historyRepository,
            TransferRepository transferRepository
    ) {
        this.courierRepository = courierRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.transferRepository = transferRepository;
    }

    private String nowBishkekTime() {
        return BISHKEK_TIME_FMT.format(Instant.now());
    }

    private Map<String, Object> buildOrderMapWithHistory(OrderEntity o) {
        List<OrderStatusHistoryEntity> history = historyRepository.findByOrderIdOrderByIdAsc(o.getId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", o.getId());
        item.put("externalApiId", o.getExternalApiId());
        item.put("apiSource", o.getApiSource());
        item.put("apiReceivedAt", o.getApiReceivedAt());
        item.put("recipientName", o.getRecipientName());
        item.put("recipientPhone", o.getRecipientPhone());
        item.put("recipientPin", o.getRecipientPin());
        item.put("address", o.getAddress());
        item.put("district", o.getDistrict());
        item.put("lat", o.getLat());
        item.put("lon", o.getLon());
        item.put("carPlate", o.getCarPlate());
        item.put("itemType", o.getItemType());
        item.put("itemQty", o.getItemQty());
        item.put("packageQrCode", o.getPackageQrCode());
        item.put("scannedAtPickup", o.getScannedAtPickup());
        item.put("pickupPoint", o.getPickupPoint());
        item.put("pickupLat", o.getPickupLat());
        item.put("pickupLon", o.getPickupLon());
        item.put("timeWindow", o.getTimeWindow());
        item.put("priority", o.getPriority());
        item.put("comment", o.getComment());
        item.put("status", o.getStatus());
        item.put("notDeliveredReason", o.getNotDeliveredReason());
        item.put("courierId", o.getCourierId());
        item.put("routeSeq", o.getRouteSeq());
        item.put("smsVerificationCode", o.getSmsVerificationCode());
        if (o.getProofPhotoUrl() != null || o.getProofCode() != null) {
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("type", "Фотоотчёт + Код + Подпись");
            proof.put("photoUrl", o.getProofPhotoUrl());
            proof.put("signature", o.getProofSignature());
            proof.put("code", o.getProofCode());
            proof.put("at", o.getDeliveredAt());
            proof.put("lat", o.getDeliveredLat());
            proof.put("lon", o.getDeliveredLon());
            item.put("proof", proof);
        } else {
            item.put("proof", null);
        }
        item.put("history", history);
        return item;
    }

    // ========================================================================
    // 1. ШЛЮЗ ВНЕШНЕГО API: ПРИЁМ ДАННЫХ КЛИЕНТА И ГОСНОМЕРОВ ДЛЯ ДОСТАВКИ
    // ========================================================================

    public record IncomingOrderDto(
            String externalApiId,
            String recipientName,
            String recipientPhone,
            String recipientPin,
            String address,
            String district,
            Double lat,
            Double lon,
            String carPlate,
            String itemType,
            String packageQrCode,
            String timeWindow,
            String priority,
            String comment
    ) {}

    @PostMapping("/integration/incoming-orders")
    @Transactional
    public ResponseEntity<Map<String, Object>> receiveOrdersFromExternalApi(
            @RequestBody List<IncomingOrderDto> incomingBatch
    ) {
        List<OrderEntity> savedOrders = new ArrayList<>();
        long baseCount = orderRepository.count();

        for (int i = 0; i < incomingBatch.size(); i++) {
            IncomingOrderDto dto = incomingBatch.get(i);
            String orderId = "PKG-2026-" + (1001 + baseCount + i);
            String cleanPlate = dto.carPlate() != null ? dto.carPlate().toUpperCase().trim() : "01 KG 000 AAA";
            String qr = dto.packageQrCode() != null && !dto.packageQrCode().isBlank()
                    ? dto.packageQrCode()
                    : "QR-AYU-" + (1001 + baseCount + i) + "-" + cleanPlate.replaceAll("\\s+", "");

            OrderEntity order = new OrderEntity();
            order.setId(orderId);
            order.setExternalApiId(dto.externalApiId() != null ? dto.externalApiId() : "API-GRS-" + (90001 + baseCount + i));
            order.setApiSource("REST API Аю Гранд / ГРС");
            order.setApiReceivedAt(nowBishkekTime());
            order.setRecipientName(dto.recipientName());
            order.setRecipientPhone(dto.recipientPhone());
            order.setRecipientPin(dto.recipientPin());
            order.setAddress(dto.address());
            order.setDistrict(dto.district() != null ? dto.district() : "г. Бишкек");
            order.setLat(dto.lat() != null ? dto.lat() : 42.8746);
            order.setLon(dto.lon() != null ? dto.lon() : 74.5998);
            order.setCarPlate(cleanPlate);
            order.setItemType(dto.itemType() != null ? dto.itemType() : "Комплект (2 шт.)");
            order.setItemQty(order.getItemType().contains("1") ? 1 : 2);
            order.setPackageQrCode(qr);
            order.setScannedAtPickup(false);
            order.setTimeWindow(dto.timeWindow() != null ? dto.timeWindow() : "09:00 – 18:00");
            order.setPriority(dto.priority() != null ? dto.priority() : "Обычный");
            order.setComment(dto.comment() != null ? dto.comment() : "Получено по API (Клиент + Госномер). Забор: Аю Гранд.");
            order.setStatus("NEW");
            order.setSmsVerificationCode(String.valueOf(1000 + new Random().nextInt(8999)));

            OrderEntity saved = orderRepository.save(order);
            savedOrders.add(saved);

            historyRepository.save(new OrderStatusHistoryEntity(
                    saved.getId(),
                    "NEW",
                    nowBishkekTime(),
                    "Внешний API (Аю Гранд)",
                    "42.8542, 74.6685",
                    "Получены данные клиента " + saved.getRecipientName() + " и госномер " + saved.getCarPlate()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "receivedCount", savedOrders.size(),
                "orders", savedOrders
        ));
    }

    // ========================================================================
    // 2. ПЕРСОНАЛЬНЫЙ API СИНХРОНИЗАЦИИ АККАУНТА КУРЬЕРА (СВОИ ЗАКАЗЫ, СВОЙ GPS, ФОТООТЧЁТ)
    // ========================================================================

    /**
     * Выдаёт данные строго конкретного аккаунта курьера: его профиль, его авто,
     * его текущие GPS-координаты и ТОЛЬКО его собственные заказы (рейс).
     */
    @GetMapping("/courier-app/{courierId}/my-account")
    public ResponseEntity<Map<String, Object>> getCourierPersonalAccountSync(@PathVariable String courierId) {
        return courierRepository.findById(courierId)
                .map(courier -> {
                    List<OrderEntity> myOrdersRaw = orderRepository.findByCourierIdOrderByRouteSeqAsc(courierId);
                    List<Map<String, Object>> myOrders = new ArrayList<>();
                    for (OrderEntity o : myOrdersRaw) {
                        myOrders.add(buildOrderMapWithHistory(o));
                    }
                    return ResponseEntity.ok(Map.of(
                            "courierAccount", courier,
                            "myOrders", myOrders,
                            "syncedAtBishkek", nowBishkekTime()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    public record TelemetryPayload(
            double lat,
            double lon,
            double speedKmh,
            int heading,
            double accuracyM,
            int battery,
            String networkType
    ) {}

    /**
     * Обновляет GPS-координаты, скорость и батарею отдельно для конкретного аккаунта курьера.
     */
    @PostMapping({"/courier-app/{id}/gps", "/couriers/{id}/telemetry"})
    @Transactional
    public ResponseEntity<CourierEntity> updateCourierTelemetry(
            @PathVariable String id,
            @RequestBody TelemetryPayload payload
    ) {
        return courierRepository.findById(id)
                .map(c -> {
                    c.setLat(payload.lat());
                    c.setLon(payload.lon());
                    c.setSpeedKmh(payload.speedKmh());
                    c.setHeading(payload.heading());
                    c.setAccuracyM(payload.accuracyM());
                    c.setBattery(payload.battery());
                    c.setNetworkType(payload.networkType() != null ? payload.networkType() : "4G LTE");
                    c.setLastSignalSec(1);
                    c.setShiftDistanceKm(Math.round((c.getShiftDistanceKm() + 0.15) * 10.0) / 10.0);
                    c.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(courierRepository.save(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    public record DeliveryPhotoReportPayload(
            String photoDataUrl,
            String smsCode,
            String signature,
            Double lat,
            Double lon,
            String comment
    ) {}

    /**
     * Курьер отправляет в админку подтверждение успешной доставки + ФОТООТЧЁТ + подпись + GPS-координаты вручения.
     */
    @PostMapping("/courier-app/{courierId}/orders/{orderId}/deliver-photo-report")
    @Transactional
    public ResponseEntity<Map<String, Object>> submitDeliveryWithPhotoReport(
            @PathVariable String courierId,
            @PathVariable String orderId,
            @RequestBody DeliveryPhotoReportPayload payload
    ) {
        Optional<OrderEntity> optOrder = orderRepository.findById(orderId);
        if (optOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        OrderEntity order = optOrder.get();
        String timeStr = nowBishkekTime();
        double dLat = payload.lat() != null ? payload.lat() : order.getLat();
        double dLon = payload.lon() != null ? payload.lon() : order.getLon();

        order.setStatus("DELIVERED");
        order.setProofPhotoUrl(payload.photoDataUrl());
        order.setProofCode(payload.smsCode());
        order.setProofSignature(payload.signature() != null ? payload.signature() : "Подпись получателя получена");
        order.setDeliveredAt(timeStr);
        order.setDeliveredLat(dLat);
        order.setDeliveredLon(dLon);
        OrderEntity savedOrder = orderRepository.save(order);

        // Обновляем статистику и координаты на аккаунте этого курьера
        courierRepository.findById(courierId).ifPresent(c -> {
            c.setDeliveredToday((c.getDeliveredToday() != null ? c.getDeliveredToday() : 0) + 1);
            c.setLat(dLat);
            c.setLon(dLon);
            c.setState("at_point");
            c.setLastSignalSec(1);
            c.setUpdatedAt(Instant.now());
            courierRepository.save(c);
        });

        historyRepository.save(new OrderStatusHistoryEntity(
                savedOrder.getId(),
                "DELIVERED",
                timeStr,
                "Курьер " + courierId,
                dLat + ", " + dLon,
                "Успешно доставлено! Фотоотчёт прикреплён, код: " + payload.smsCode()
                        + (payload.comment() != null && !payload.comment().isBlank() ? " (" + payload.comment() + ")" : "")
        ));

        return ResponseEntity.ok(buildOrderMapWithHistory(savedOrder));
    }

    // ========================================================================
    // 3. ХРАНЕНИЕ И УПРАВЛЕНИЕ ДАННЫМИ КАЖДОГО КУРЬЕРА В АДМИНКЕ
    // ========================================================================

    @GetMapping("/couriers")
    public List<CourierEntity> getAllCouriers() {
        return courierRepository.findAllByOrderByIdAsc();
    }

    @GetMapping("/couriers/{id}")
    public ResponseEntity<Map<String, Object>> getCourierFullStorageProfile(@PathVariable String id) {
        return courierRepository.findById(id)
                .map(courier -> {
                    List<OrderEntity> assignedOrders = orderRepository.findByCourierIdOrderByRouteSeqAsc(id);
                    return ResponseEntity.ok(Map.of(
                            "courier", courier,
                            "assignedOrders", assignedOrders
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/couriers")
    @Transactional
    public ResponseEntity<CourierEntity> createOrUpdateCourier(@RequestBody CourierEntity courier) {
        if (courier.getId() == null || courier.getId().isBlank()) {
            long nextNum = courierRepository.count() + 1;
            courier.setId(String.format("CR-%03d", nextNum));
        }
        if (courier.getDeviceId() == null || courier.getDeviceId().isBlank()) {
            courier.setDeviceId("AND-POSTKG-" + (88000 + courierRepository.count() + 1));
        }
        courier.setUpdatedAt(Instant.now());
        CourierEntity saved = courierRepository.save(courier);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/couriers/{id}/sos")
    @Transactional
    public ResponseEntity<CourierEntity> triggerCourierSos(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        return courierRepository.findById(id)
                .map(c -> {
                    String reason = body.getOrDefault("reason", "SOS: Не может продолжить рейс");
                    c.setState("problem");
                    c.setProblemReason(reason);
                    c.setSpeedKmh(0.0);
                    c.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(courierRepository.save(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================================================================
    // 4. ЗАКАЗЫ, НАЗНАЧЕНИЕ И ПЕРЕДАЧА В АДМИНКЕ
    // ========================================================================

    @GetMapping("/orders")
    public List<Map<String, Object>> getAllOrdersWithHistory() {
        List<OrderEntity> allOrders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderEntity o : allOrders) {
            result.add(buildOrderMapWithHistory(o));
        }
        return result;
    }

    @PostMapping("/orders/{id}/assign")
    @Transactional
    public ResponseEntity<OrderEntity> assignOrderToCourier(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String courierId = body.get("courierId");
        return orderRepository.findById(id)
                .map(o -> {
                    o.setCourierId(courierId);
                    o.setStatus("ASSIGNED");
                    OrderEntity saved = orderRepository.save(o);
                    historyRepository.save(new OrderStatusHistoryEntity(
                            saved.getId(),
                            "ASSIGNED",
                            nowBishkekTime(),
                            "Диспетчер",
                            saved.getLat() + ", " + saved.getLon(),
                            "Закреплён за курьером " + courierId
                    ));
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/{id}/status")
    @Transactional
    public ResponseEntity<OrderEntity> changeOrderStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String newStatus = body.get("status");
        String comment = body.getOrDefault("comment", "Обновление статуса");
        return orderRepository.findById(id)
                .map(o -> {
                    o.setStatus(newStatus);
                    if ("PICKED_UP".equals(newStatus)) {
                        o.setScannedAtPickup(true);
                    }
                    if ("NOT_DELIVERED".equals(newStatus)) {
                        o.setNotDeliveredReason(comment);
                    }
                    OrderEntity saved = orderRepository.save(o);
                    historyRepository.save(new OrderStatusHistoryEntity(
                            saved.getId(),
                            newStatus,
                            nowBishkekTime(),
                            saved.getCourierId() != null ? saved.getCourierId() : "Диспетчер",
                            saved.getLat() + ", " + saved.getLon(),
                            comment
                    ));
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transfers")
    public List<TransferEntity> getAllTransfers() {
        return transferRepository.findAllByOrderByCreatedUtcDesc();
    }

    @PostMapping("/transfers")
    @Transactional
    public ResponseEntity<TransferEntity> createTransfer(@RequestBody TransferEntity transfer) {
        String trId = "TRF-2026-0" + (transferRepository.count() + 1);
        transfer.setId(trId);
        transfer.setCode(String.valueOf(100000 + new Random().nextInt(899999)));
        transfer.setStatus("PENDING");
        transfer.setCreatedAt(nowBishkekTime());
        TransferEntity saved = transferRepository.save(transfer);

        for (String orderId : saved.getOrderIds()) {
            orderRepository.findById(orderId).ifPresent(o -> {
                String st = "RETURN_TO_AYU_GRAND".equals(saved.getMethod()) ? "RETURN" : "IN_TRANSFER";
                o.setStatus(st);
                orderRepository.save(o);
                historyRepository.save(new OrderStatusHistoryEntity(
                        o.getId(),
                        st,
                        nowBishkekTime(),
                        "Диспетчер (Мастер передачи)",
                        "42.8542, 74.6685",
                        "Запущена передача " + trId + " от " + saved.getFromCourierId() + " к " + saved.getToCourierId()
                ));
            });
        }
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/transfers/{id}/confirm")
    @Transactional
    public ResponseEntity<TransferEntity> confirmTransfer(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String comment = body != null ? body.getOrDefault("comment", "Подтверждено по QR") : "Подтверждено по QR";
        return transferRepository.findById(id)
                .map(tr -> {
                    tr.setStatus("CONFIRMED");
                    tr.setConfirmedAt(nowBishkekTime());
                    TransferEntity saved = transferRepository.save(tr);

                    for (String orderId : saved.getOrderIds()) {
                        orderRepository.findById(orderId).ifPresent(o -> {
                            String nextSt = saved.getToCourierId() != null ? "PICKED_UP" : "RETURNED";
                            o.setCourierId(saved.getToCourierId());
                            o.setStatus(nextSt);
                            o.setScannedAtPickup(saved.getToCourierId() != null);
                            orderRepository.save(o);
                            historyRepository.save(new OrderStatusHistoryEntity(
                                    o.getId(),
                                    nextSt,
                                    nowBishkekTime(),
                                    saved.getToCourierId() != null ? saved.getToCourierId() : "Диспетчер",
                                    "42.8542, 74.6685",
                                    "Передача " + saved.getId() + " подтверждена (" + comment + ")"
                            ));
                        });
                    }
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
