package kg.post.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Таблица orders в PostgreSQL — хранение заказов, полученных по API (Клиент + Госномер),
 * закреплённых за конкретным аккаунтом курьера, включая ФОТООТЧЁТ курьера о доставке.
 */

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id; // Например: PKG-2026-1001

    @Column(name = "external_api_id", length = 64)
    private String externalApiId;

    @Column(name = "api_source", length = 64)
    private String apiSource = "REST API Аю Гранд / ГРС";

    @Column(name = "api_received_at", length = 32)
    private String apiReceivedAt;

    // Данные клиента (получателя), пришедшие по API
    @Column(name = "recipient_name", length = 150, nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32, nullable = false)
    private String recipientPhone;

    @Column(name = "recipient_pin", length = 32)
    private String recipientPin;

    @Column(name = "address", columnDefinition = "TEXT", nullable = false)
    private String address;

    @Column(name = "district", length = 64)
    private String district;

    @Column(name = "lat", nullable = false)
    private Double lat = 42.8746;

    @Column(name = "lon", nullable = false)
    private Double lon = 74.5998;

    // Данные доставляемого номерного знака (какой номер какому клиенту)
    @Column(name = "car_plate", length = 32, nullable = false)
    private String carPlate; // Например: 01 KG 777 AAA

    @Column(name = "item_type", length = 32, nullable = false)
    private String itemType = "Комплект (2 шт.)";

    @Column(name = "item_qty", nullable = false)
    private Integer itemQty = 2;

    @Column(name = "package_qr_code", length = 64, nullable = false, unique = true)
    private String packageQrCode;

    @Column(name = "scanned_at_pickup", nullable = false)
    private Boolean scannedAtPickup = false;

    // Точка забора (по умолчанию: Аю Гранд)
    @Column(name = "pickup_point", length = 150)
    private String pickupPoint = "Аю Гранд (Пункт выдачи ГРНЗ POST.KG)";

    @Column(name = "pickup_lat")
    private Double pickupLat = 42.8542;

    @Column(name = "pickup_lon")
    private Double pickupLon = 74.6685;

    @Column(name = "time_window", length = 48)
    private String timeWindow = "09:00 – 18:00";

    @Column(name = "priority", length = 24)
    private String priority = "Обычный";

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    // Статус заказа (12 статусов ТЗ)
    @Column(name = "status", length = 32, nullable = false)
    private String status = "NEW";

    @Column(name = "not_delivered_reason", length = 128)
    private String notDeliveredReason;

    // Закрепление за индивидуальным аккаунтом курьера
    @Column(name = "courier_id", length = 16)
    private String courierId;

    @Column(name = "route_seq")
    private Integer routeSeq = 1;

    @Column(name = "sms_verification_code", length = 16)
    private String smsVerificationCode;

    // ФОТООТЧЁТ И ДАННЫЕ О ВРУЧЕНИИ ОТ КУРЬЕРА
    @Column(name = "proof_photo_url", columnDefinition = "TEXT")
    private String proofPhotoUrl;

    @Column(name = "proof_signature", length = 128)
    private String proofSignature;

    @Column(name = "proof_code", length = 32)
    private String proofCode;

    @Column(name = "delivered_at", length = 32)
    private String deliveredAt;

    @Column(name = "delivered_lat")
    private Double deliveredLat;

    @Column(name = "delivered_lon")
    private Double deliveredLon;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public OrderEntity() {}

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExternalApiId() { return externalApiId; }
    public void setExternalApiId(String externalApiId) { this.externalApiId = externalApiId; }

    public String getApiSource() { return apiSource; }
    public void setApiSource(String apiSource) { this.apiSource = apiSource; }

    public String getApiReceivedAt() { return apiReceivedAt; }
    public void setApiReceivedAt(String apiReceivedAt) { this.apiReceivedAt = apiReceivedAt; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getRecipientPin() { return recipientPin; }
    public void setRecipientPin(String recipientPin) { this.recipientPin = recipientPin; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getCarPlate() { return carPlate; }
    public void setCarPlate(String carPlate) { this.carPlate = carPlate; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public Integer getItemQty() { return itemQty; }
    public void setItemQty(Integer itemQty) { this.itemQty = itemQty; }

    public String getPackageQrCode() { return packageQrCode; }
    public void setPackageQrCode(String packageQrCode) { this.packageQrCode = packageQrCode; }

    public Boolean getScannedAtPickup() { return scannedAtPickup; }
    public void setScannedAtPickup(Boolean scannedAtPickup) { this.scannedAtPickup = scannedAtPickup; }

    public String getPickupPoint() { return pickupPoint; }
    public void setPickupPoint(String pickupPoint) { this.pickupPoint = pickupPoint; }

    public Double getPickupLat() { return pickupLat; }
    public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }

    public Double getPickupLon() { return pickupLon; }
    public void setPickupLon(Double pickupLon) { this.pickupLon = pickupLon; }

    public String getTimeWindow() { return timeWindow; }
    public void setTimeWindow(String timeWindow) { this.timeWindow = timeWindow; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotDeliveredReason() { return notDeliveredReason; }
    public void setNotDeliveredReason(String notDeliveredReason) { this.notDeliveredReason = notDeliveredReason; }

    public String getCourierId() { return courierId; }
    public void setCourierId(String courierId) { this.courierId = courierId; }

    public Integer getRouteSeq() { return routeSeq; }
    public void setRouteSeq(Integer routeSeq) { this.routeSeq = routeSeq; }

    public String getSmsVerificationCode() { return smsVerificationCode; }
    public void setSmsVerificationCode(String smsVerificationCode) { this.smsVerificationCode = smsVerificationCode; }

    public String getProofPhotoUrl() { return proofPhotoUrl; }
    public void setProofPhotoUrl(String proofPhotoUrl) { this.proofPhotoUrl = proofPhotoUrl; }

    public String getProofSignature() { return proofSignature; }
    public void setProofSignature(String proofSignature) { this.proofSignature = proofSignature; }

    public String getProofCode() { return proofCode; }
    public void setProofCode(String proofCode) { this.proofCode = proofCode; }

    public String getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(String deliveredAt) { this.deliveredAt = deliveredAt; }

    public Double getDeliveredLat() { return deliveredLat; }
    public void setDeliveredLat(Double deliveredLat) { this.deliveredLat = deliveredLat; }

    public Double getDeliveredLon() { return deliveredLon; }
    public void setDeliveredLon(Double deliveredLon) { this.deliveredLon = deliveredLon; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
