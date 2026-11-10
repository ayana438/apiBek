package kg.post.courier.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Таблица couriers в PostgreSQL — хранение полных данных каждого курьера:
 * ФИО, телефон, PIN-код, автомобиль (марка, цвет, госномер),
 * привязанное Android-устройство (device_id, модель, батарея, сеть),
 * статус смены, текущие координаты и накопительная статистика доставок.
 */
@Entity
@Table(name = "couriers")
public class CourierEntity {

    @Id
    @Column(name = "id", length = 16, nullable = false)
    private String id; // Например: CR-001

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "phone", length = 32, nullable = false, unique = true)
    private String phone;

    @Column(name = "pin_code", length = 16, nullable = false)
    private String pinCode = "1234";

    @Column(name = "account_status", length = 24, nullable = false)
    private String accountStatus = "Активен"; // Активен | Заблокирован

    // Состояние на карте: moving | at_point | idle | problem | offline
    @Column(name = "state", length = 24, nullable = false)
    private String state = "moving";

    @Column(name = "problem_reason")
    private String problemReason;

    @Column(name = "idle_minutes", nullable = false)
    private Integer idleMinutes = 0;

    // Данные автомобиля курьера
    @Column(name = "vehicle_make", length = 64, nullable = false)
    private String vehicleMake;

    @Column(name = "vehicle_color", length = 48, nullable = false)
    private String vehicleColor = "Белый";

    @Column(name = "vehicle_plate", length = 32, nullable = false)
    private String vehiclePlate;

    @Column(name = "vehicle_type", length = 32, nullable = false)
    private String vehicleType = "Легковой авто";

    // Привязанное устройство курьера и Firebase Push-токен
    @Column(name = "device_id", length = 128, unique = true)
    private String deviceId;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "last_signal_sec", nullable = false)
    private Integer lastSignalSec = 1;

    // Данные смены и выработки
    @Column(name = "on_shift", nullable = false)
    private Boolean onShift = true;

    @Column(name = "shift_started_at", length = 32)
    private String shiftStartedAt = "08:30";

    @Column(name = "shift_ended_at", length = 32)
    private String shiftEndedAt = "В работе";

    @Column(name = "shift_distance_km", nullable = false)
    private Double shiftDistanceKm = 0.0;

    @Column(name = "total_orders_today", nullable = false)
    private Integer totalOrdersToday = 0;

    @Column(name = "delivered_today", nullable = false)
    private Integer deliveredToday = 0;

    @Column(name = "failed_today", nullable = false)
    private Integer failedToday = 0;

    @Column(name = "avg_delivery_time_min", nullable = false)
    private Integer avgDeliveryTimeMin = 25;

    @Column(name = "max_orders_limit", nullable = false)
    private Integer maxOrdersLimit = 30;

    // Текущие GPS-координаты курьера
    @Column(name = "lat", nullable = false)
    private Double lat = 42.8542;

    @Column(name = "lon", nullable = false)
    private Double lon = 74.6685;

    @Column(name = "speed_kmh", nullable = false)
    private Double speedKmh = 0.0;

    @Column(name = "heading", nullable = false)
    private Integer heading = 0;

    @Column(name = "accuracy_m", nullable = false)
    private Double accuracyM = 3.5;

    @Column(name = "registered_at", length = 32)
    private String registeredAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CourierEntity() {}

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getProblemReason() { return problemReason; }
    public void setProblemReason(String problemReason) { this.problemReason = problemReason; }

    public Integer getIdleMinutes() { return idleMinutes; }
    public void setIdleMinutes(Integer idleMinutes) { this.idleMinutes = idleMinutes; }

    public String getVehicleMake() { return vehicleMake; }
    public void setVehicleMake(String vehicleMake) { this.vehicleMake = vehicleMake; }

    public String getVehicleColor() { return vehicleColor; }
    public void setVehicleColor(String vehicleColor) { this.vehicleColor = vehicleColor; }

    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public Integer getLastSignalSec() { return lastSignalSec; }
    public void setLastSignalSec(Integer lastSignalSec) { this.lastSignalSec = lastSignalSec; }

    public Boolean getOnShift() { return onShift; }
    public void setOnShift(Boolean onShift) { this.onShift = onShift; }

    public String getShiftStartedAt() { return shiftStartedAt; }
    public void setShiftStartedAt(String shiftStartedAt) { this.shiftStartedAt = shiftStartedAt; }

    public String getShiftEndedAt() { return shiftEndedAt; }
    public void setShiftEndedAt(String shiftEndedAt) { this.shiftEndedAt = shiftEndedAt; }

    public Double getShiftDistanceKm() { return shiftDistanceKm; }
    public void setShiftDistanceKm(Double shiftDistanceKm) { this.shiftDistanceKm = shiftDistanceKm; }

    public Integer getTotalOrdersToday() { return totalOrdersToday; }
    public void setTotalOrdersToday(Integer totalOrdersToday) { this.totalOrdersToday = totalOrdersToday; }

    public Integer getDeliveredToday() { return deliveredToday; }
    public void setDeliveredToday(Integer deliveredToday) { this.deliveredToday = deliveredToday; }

    public Integer getFailedToday() { return failedToday; }
    public void setFailedToday(Integer failedToday) { this.failedToday = failedToday; }

    public Integer getAvgDeliveryTimeMin() { return avgDeliveryTimeMin; }
    public void setAvgDeliveryTimeMin(Integer avgDeliveryTimeMin) { this.avgDeliveryTimeMin = avgDeliveryTimeMin; }

    public Integer getMaxOrdersLimit() { return maxOrdersLimit; }
    public void setMaxOrdersLimit(Integer maxOrdersLimit) { this.maxOrdersLimit = maxOrdersLimit; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public Double getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(Double speedKmh) { this.speedKmh = speedKmh; }

    public Integer getHeading() { return heading; }
    public void setHeading(Integer heading) { this.heading = heading; }

    public Double getAccuracyM() { return accuracyM; }
    public void setAccuracyM(Double accuracyM) { this.accuracyM = accuracyM; }

    public String getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

