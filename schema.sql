-- ============================================================================
-- POST.KG — Система курьерской службы доставки государственных номерных знаков
-- Полная схема базы данных PostgreSQL 16 + PostGIS
-- Часовой пояс хранения: UTC (отображение в интерфейсах: Asia/Bishkek, UTC+6)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

SET TIME ZONE 'UTC';

-- ----------------------------------------------------------------------------
-- 1. ПОЛЬЗОВАТЕЛИ СИСТЕМЫ (Администраторы, Диспетчеры, Курьеры)
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role VARCHAR(24) NOT NULL CHECK (role IN ('ADMIN', 'DISPATCHER', 'COURIER')),
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(120) UNIQUE,
    password_hash TEXT NOT NULL,
    pin_hash TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 2. ХРАНЕНИЕ ДАННЫХ КАЖДОГО КУРЬЕРА (Профиль, авто, устройство, телеметрия)
-- ----------------------------------------------------------------------------
CREATE TABLE couriers (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    courier_code VARCHAR(16) NOT NULL UNIQUE,          -- Например: CR-001
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(32) NOT NULL UNIQUE,
    photo_url TEXT,
    -- Данные автомобиля курьера
    vehicle_make VARCHAR(64) NOT NULL,                 -- Марка/модель (Toyota Aqua, Honda Fit и т.д.)
    vehicle_color VARCHAR(48) NOT NULL DEFAULT 'Белый',-- Цвет автомобиля
    vehicle_plate VARCHAR(32) NOT NULL,                -- Госномер авто курьера (01 KG 123 AAA)
    vehicle_type VARCHAR(32) NOT NULL DEFAULT 'Автомобиль',
    -- Привязка устройства и безопасность
    device_id VARCHAR(128) UNIQUE,                     -- Уникальный Android Device ID
    device_model VARCHAR(128),                         -- Например: Samsung Galaxy A54 / Xiaomi Redmi Note 13
    android_version VARCHAR(32),
    fcm_token TEXT,                                    -- Токен Firebase Cloud Messaging для Push
    -- Операционный статус и текущее состояние на смене
    account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE', 'BLOCKED')),
    shift_status VARCHAR(24) NOT NULL DEFAULT 'OFFLINE' CHECK (
        shift_status IN ('MOVING', 'AT_POINT', 'IDLE', 'PROBLEM_SOS', 'OFFLINE')
    ),
    max_orders_limit INTEGER NOT NULL DEFAULT 30,      -- Лимит заказов в рейсе (по умолчанию 30)
    -- Последняя известная геолокация и телеметрия устройства
    last_lat DOUBLE PRECISION DEFAULT 42.8546,
    last_lon DOUBLE PRECISION DEFAULT 74.6285,
    last_geom GEOMETRY(Point, 4326),
    last_speed_kmh DOUBLE PRECISION DEFAULT 0,
    last_heading DOUBLE PRECISION DEFAULT 0,
    last_accuracy_m DOUBLE PRECISION DEFAULT 5.0,
    battery_level INTEGER DEFAULT 100 CHECK (battery_level BETWEEN 0 AND 100),
    network_type VARCHAR(24) DEFAULT '4G',
    last_signal_at TIMESTAMPTZ DEFAULT NOW(),
    -- Накопительная статистика по курьеру
    total_delivered INTEGER NOT NULL DEFAULT 0,
    total_not_delivered INTEGER NOT NULL DEFAULT 0,
    total_distance_km DOUBLE PRECISION NOT NULL DEFAULT 0,
    avg_delivery_minutes INTEGER NOT NULL DEFAULT 25,
    notes TEXT,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_couriers_geom ON couriers USING GIST (last_geom);
CREATE INDEX idx_couriers_shift_status ON couriers (shift_status);

-- ----------------------------------------------------------------------------
-- 3. СМЕНЫ КУРЬЕРОВ (Учёт рабочего времени, пробега и доставок за смену)
-- ----------------------------------------------------------------------------
CREATE TABLE shifts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    courier_id UUID NOT NULL REFERENCES couriers(user_id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    start_lat DOUBLE PRECISION,
    start_lon DOUBLE PRECISION,
    end_lat DOUBLE PRECISION,
    end_lon DOUBLE PRECISION,
    distance_km DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    orders_assigned INTEGER NOT NULL DEFAULT 0,
    orders_delivered INTEGER NOT NULL DEFAULT 0,
    orders_failed INTEGER NOT NULL DEFAULT 0,
    idle_minutes INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_shifts_courier_active ON shifts (courier_id, is_active);

-- ----------------------------------------------------------------------------
-- 4. ТОЧКИ ЗАБОРА НОМЕРОВ (По умолчанию: Аю Гранд)
-- ----------------------------------------------------------------------------
CREATE TABLE pickup_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(32) NOT NULL UNIQUE DEFAULT 'AYU_GRAND',
    name VARCHAR(150) NOT NULL DEFAULT 'Аю Гранд (Точка выдачи ГРНЗ)',
    address TEXT NOT NULL DEFAULT 'г. Бишкек, ул. Чокана Валиханова 2 (ТЦ Аю Гранд)',
    lat DOUBLE PRECISION NOT NULL DEFAULT 42.8542,
    lon DOUBLE PRECISION NOT NULL DEFAULT 74.6685,
    geofence_radius_m INTEGER NOT NULL DEFAULT 100,
    working_hours VARCHAR(64) DEFAULT 'Пн-Сб 08:30 - 18:00',
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- ----------------------------------------------------------------------------
-- 5. ВХОДЯЩИЕ ПАКЕТЫ API (Данные клиентов и номеров из внешней системы)
-- ----------------------------------------------------------------------------
CREATE TABLE api_incoming_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_batch_id VARCHAR(64) NOT NULL UNIQUE,
    source_system VARCHAR(64) NOT NULL DEFAULT 'AYU_GRAND_API',
    raw_payload JSONB NOT NULL,
    orders_count INTEGER NOT NULL DEFAULT 1,
    processed_status VARCHAR(24) NOT NULL DEFAULT 'SUCCESS',
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 6. ЗАКАЗЫ (Какой номерной знак какому клиенту доставляется)
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    number VARCHAR(32) NOT NULL UNIQUE,                -- Номер заказа (например: PKG-2026-1001)
    external_api_id VARCHAR(64) UNIQUE,                -- ID записи из внешнего API
    api_batch_id UUID REFERENCES api_incoming_batches(id),
    -- Данные получателя (клиента), полученные по API
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(32) NOT NULL,
    recipient_pin VARCHAR(20),                         -- ПИН / ИНН или номер документа получателя (из API)
    address TEXT NOT NULL,
    district VARCHAR(64),
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    geom GEOMETRY(Point, 4326),
    -- Данные доставляемого номерного знака (ГРНЗ)
    car_plate VARCHAR(32) NOT NULL,                    -- Госномер для доставки (напр. 01 KG 777 AAA)
    item_type VARCHAR(24) NOT NULL DEFAULT 'SET' CHECK (item_type IN ('SET', 'SINGLE')), -- Комплект (2 шт) или Одиночный (1 шт)
    item_qty INTEGER NOT NULL DEFAULT 2,
    package_qr_code VARCHAR(64) NOT NULL UNIQUE,       -- QR / штрих-код на упаковке в Аю Гранд
    scanned_at_pickup BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_at_return BOOLEAN NOT NULL DEFAULT FALSE,
    -- Параметры доставки
    pickup_point_id UUID REFERENCES pickup_points(id),
    time_window VARCHAR(48) DEFAULT '09:00 - 18:00',
    priority VARCHAR(24) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('NORMAL', 'HIGH', 'URGENT')),
    comment TEXT,
    -- Статус заказа (12 статусов по ТЗ, раздел 4)
    status VARCHAR(32) NOT NULL DEFAULT 'NEW' CHECK (
        status IN (
            'NEW',            -- Новый (получен по API / создан, не назначен)
            'ASSIGNED',       -- Назначен (закреплён за курьером)
            'ACCEPTED',       -- Принят (курьер подтвердил рейс)
            'PICKED_UP',      -- Забран (курьер получил номера в Аю Гранд по скану QR)
            'IN_TRANSIT',     -- В пути (курьер выехал к клиенту)
            'ARRIVED',        -- Прибыл (курьер на адресе, геозона 100 м или вручную)
            'DELIVERED',      -- Доставлен (подтверждено получателем)
            'NOT_DELIVERED',  -- Не доставлен (с причиной)
            'IN_TRANSFER',    -- На передаче другому курьеру (раздел 6)
            'RETURN',         -- Возврат (номера везут обратно в Аю Гранд)
            'RETURNED',       -- Возвращён (подтверждён сканом в Аю Гранд)
            'CANCELLED'       -- Отменён диспетчером (с причиной)
        )
    ),
    not_delivered_reason VARCHAR(64),
    cancel_reason TEXT,
    -- Закрепление за курьером
    courier_id UUID REFERENCES couriers(user_id),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_courier ON orders (courier_id);
CREATE INDEX idx_orders_car_plate ON orders (car_plate);
CREATE INDEX idx_orders_phone ON orders (recipient_phone);

-- ----------------------------------------------------------------------------
-- 7. ИСТОРИЯ СТАТУСОВ ЗАКАЗА (Кто, когда, откуда, комментарий)
-- ----------------------------------------------------------------------------
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    by_user UUID REFERENCES users(id),
    lat DOUBLE PRECISION,
    lon DOUBLE PRECISION,
    comment TEXT
);

CREATE INDEX idx_order_history_order ON order_status_history (order_id, at DESC);

-- ----------------------------------------------------------------------------
-- 8. ПОДТВЕРЖДЕНИЯ ВРУЧЕНИЯ (Код SMS / Подпись на экране / Фото)
-- ----------------------------------------------------------------------------
CREATE TABLE order_proofs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    type VARCHAR(24) NOT NULL CHECK (type IN ('CODE', 'SIGNATURE', 'PHOTO')),
    verification_code VARCHAR(16),
    file_url TEXT,
    lat DOUBLE PRECISION,
    lon DOUBLE PRECISION,
    at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ----------------------------------------------------------------------------
-- 9. РЕЙСЫ И МАРШРУТНЫЕ ТОЧКИ КУРЬЕРА (Порядок объезда 10+ заказов)
-- ----------------------------------------------------------------------------
CREATE TABLE route_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shift_id UUID REFERENCES shifts(id) ON DELETE CASCADE,
    courier_id UUID NOT NULL REFERENCES couriers(user_id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,                              -- Порядковый номер точки в маршруте
    eta_at TIMESTAMPTZ,
    distance_from_prev_km DOUBLE PRECISION,
    UNIQUE (courier_id, order_id)
);

CREATE INDEX idx_route_points_courier_seq ON route_points (courier_id, seq);

-- ----------------------------------------------------------------------------
-- 10. ИСТОРИЯ GPS-ЛОКАЦИЙ КУРЬЕРОВ (Детальное хранение 90 дней)
-- ----------------------------------------------------------------------------
CREATE TABLE locations (
    id BIGSERIAL PRIMARY KEY,
    courier_id UUID NOT NULL REFERENCES couriers(user_id) ON DELETE CASCADE,
    shift_id UUID REFERENCES shifts(id) ON DELETE SET NULL,
    at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    device_time TIMESTAMPTZ,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    geom GEOMETRY(Point, 4326),
    speed DOUBLE PRECISION DEFAULT 0,
    heading DOUBLE PRECISION DEFAULT 0,
    accuracy DOUBLE PRECISION DEFAULT 5.0,
    battery INTEGER DEFAULT 100,
    network_type VARCHAR(24) DEFAULT '4G'
);

CREATE INDEX idx_locations_courier_time ON locations (courier_id, at DESC);

-- ----------------------------------------------------------------------------
-- 11. ПЕРЕДАЧА ЗАКАЗОВ МЕЖДУ КУРЬЕРАМИ (Раздел 6 — контроль ответственности)
-- ----------------------------------------------------------------------------
CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_code VARCHAR(16) NOT NULL UNIQUE,         -- 6-значный код / QR сессии передачи
    from_courier UUID NOT NULL REFERENCES couriers(user_id),
    to_courier UUID REFERENCES couriers(user_id),      -- NULL если возврат напрямую в Аю Гранд
    method VARCHAR(32) NOT NULL CHECK (
        method IN ('MEET_ON_SITE', 'RETURN_TO_AYU_GRAND', 'INSTANT_NOT_PICKED_UP')
    ),
    reason VARCHAR(64) NOT NULL,                       -- Поломка авто, ДТП, здоровье, простой и т.д.
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'ACCEPTED_BY_RECIPIENT', 'CONFIRMED', 'PARTIAL_PROBLEM', 'REJECTED', 'CANCELLED')
    ),
    meet_lat DOUBLE PRECISION,
    meet_lon DOUBLE PRECISION,
    requested_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    confirmed_by UUID REFERENCES users(id),            -- Курьер (по QR) или диспетчер (вручную с комментарием)
    manual_confirm_comment TEXT
);

CREATE TABLE transfer_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id UUID NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    package_qr_code VARCHAR(64) NOT NULL,
    received BOOLEAN NOT NULL DEFAULT FALSE,
    scanned_at TIMESTAMPTZ,
    UNIQUE (transfer_id, order_id)
);

-- ----------------------------------------------------------------------------
-- 12. ИНЦИДЕНТЫ (Простой, длительный простой, потеря связи, SOS, низкий заряд)
-- ----------------------------------------------------------------------------
CREATE TABLE incidents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    courier_id UUID NOT NULL REFERENCES couriers(user_id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL CHECK (
        type IN ('IDLE_10M', 'LONG_IDLE_20M', 'SIGNAL_LOST', 'LOW_BATTERY', 'SOS_BREAKDOWN', 'SOS_ACCIDENT', 'SOS_HEALTH', 'SOS_FLAT_TIRE', 'SOS_OTHER', 'TRANSFER_DISCREPANCY')
    ),
    source VARCHAR(24) NOT NULL CHECK (source IN ('AUTO', 'SOS', 'DISPATCHER')),
    lat DOUBLE PRECISION,
    lon DOUBLE PRECISION,
    comment TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id)
);

-- ----------------------------------------------------------------------------
-- 13. СИСТЕМНЫЕ НАСТРОЙКИ, УВЕДОМЛЕНИЯ И НЕИЗМЕНЯЕМЫЙ ЖУРНАЛ АУДИТА
-- ----------------------------------------------------------------------------
CREATE TABLE settings (
    key VARCHAR(64) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES users(id)
);

INSERT INTO settings (key, value, description) VALUES
('IDLE_WARNING_MINUTES', '10', 'Порог предупреждения о простое курьера (< 20 м), минут'),
('IDLE_CRITICAL_MINUTES', '20', 'Порог длительного простоя (красный индикатор + звук), минут'),
('SIGNAL_LOST_MINUTES', '5', 'Порог потери GPS/сети курьера, минут'),
('ARRIVAL_GEOFENCE_METERS', '100', 'Радиус геозоны автоматического статуса Прибыл и точки Аю Гранд, метров'),
('GPS_INTERVAL_MOVING_SEC', '5', 'Частота отправки GPS при движении, секунд'),
('GPS_INTERVAL_IDLE_SEC', '30', 'Частота отправки GPS в покое, секунд'),
('COURIER_SOFT_ORDER_LIMIT', '30', 'Мягкий лимит заказов на одного курьера в рейсе'),
('LOW_BATTERY_PERCENT', '15', 'Порог уведомления о низком заряде батареи курьера, %');

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id UUID REFERENCES users(id),
    actor_name VARCHAR(150) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    ip_or_device VARCHAR(128) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64),
    details TEXT NOT NULL
);
