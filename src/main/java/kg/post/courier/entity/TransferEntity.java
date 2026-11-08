package kg.post.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Таблица transfers в PostgreSQL — хранение передач заказов между курьерами при поломке/SOS
 * с контролем физической ответственности (Раздел 6 ТЗ).
 */
@Entity
@Table(name = "transfers")
public class TransferEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id; // Например: TRF-2026-01

    @Column(name = "code", length = 16, nullable = false)
    private String code; // 6-значный код / QR сессии

    @Column(name = "from_courier_id", length = 16, nullable = false)
    private String fromCourierId;

    @Column(name = "to_courier_id", length = 16)
    private String toCourierId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "transfer_order_ids", joinColumns = @JoinColumn(name = "transfer_id"))
    @Column(name = "order_id")
    private List<String> orderIds = new ArrayList<>();

    @Column(name = "method", length = 32, nullable = false)
    private String method; // MEET_ON_SITE | RETURN_TO_AYU_GRAND | INSTANT_NOT_PICKED_UP

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "status", length = 24, nullable = false)
    private String status = "PENDING"; // PENDING | CONFIRMED | CANCELLED

    @Column(name = "created_at", length = 32)
    private String createdAt;

    @Column(name = "confirmed_at", length = 32)
    private String confirmedAt;

    @Column(name = "created_utc", nullable = false)
    private Instant createdUtc = Instant.now();

    public TransferEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFromCourierId() { return fromCourierId; }
    public void setFromCourierId(String fromCourierId) { this.fromCourierId = fromCourierId; }
    public String getToCourierId() { return toCourierId; }
    public void setToCourierId(String toCourierId) { this.toCourierId = toCourierId; }
    public List<String> getOrderIds() { return orderIds; }
    public void setOrderIds(List<String> orderIds) { this.orderIds = orderIds; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(String confirmedAt) { this.confirmedAt = confirmedAt; }
}

