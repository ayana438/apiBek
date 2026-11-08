package kg.post.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Таблица order_status_history в PostgreSQL — история всех изменений статуса заказа
 * (кто, когда, координаты, комментарий).
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", length = 32, nullable = false)
    private String orderId;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "at_time", length = 32, nullable = false)
    private String at;

    @Column(name = "by_actor", length = 128, nullable = false)
    private String by;

    @Column(name = "coords", length = 64)
    private String coords;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public OrderStatusHistoryEntity() {}

    public OrderStatusHistoryEntity(String orderId, String status, String at, String by, String coords, String comment) {
        this.orderId = orderId;
        this.status = status;
        this.at = at;
        this.by = by;
        this.coords = coords;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAt() { return at; }
    public void setAt(String at) { this.at = at; }
    public String getBy() { return by; }
    public void setBy(String by) { this.by = by; }
    public String getCoords() { return coords; }
    public void setCoords(String coords) { this.coords = coords; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

