import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { generateInitialCouriers, generateInitialOrders } from '../src/postKgData.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DB_FILE = path.join(__dirname, 'post_kg_database.json');
const PORT = 8080;

function loadDb() {
  try {
    if (fs.existsSync(DB_FILE)) {
      return JSON.parse(fs.readFileSync(DB_FILE, 'utf-8'));
    }
  } catch (e) {}
  const couriers = generateInitialCouriers();
  const orders = generateInitialOrders(couriers);
  const initial = { couriers, orders, transfers: [] };
  fs.writeFileSync(DB_FILE, JSON.stringify(initial, null, 2), 'utf-8');
  return initial;
}

function saveDb(db) {
  fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2), 'utf-8');
}

let db = loadDb();

function nowBishkek() {
  return new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

// Мониторинг статусов курьеров по ТЗ POST.KG:
// "moving" (В движении), "at_point" (На точке), "idle" (Простой), "problem" (SOS/Проблема), "offline" (Оффлайн)
// Если нет сигнала GPS >= 10 минут (600 секунд) -> статус автоматически переключается в "offline"
function refreshCourierStatuses(couriers) {
  const now = Date.now();
  for (const c of couriers) {
    if (!c.lastSignalTimestamp) {
      if (c.state === 'offline') {
        c.lastSignalTimestamp = now - 650 * 1000;
      } else {
        const sec = c.lastSignalSec || 5;
        c.lastSignalTimestamp = now - sec * 1000;
      }
    }
    const diffSec = Math.floor((now - c.lastSignalTimestamp) / 1000);
    c.lastSignalSec = diffSec;

    // Если нет сигнала 10 минут (>= 600 секунд) — оффлайн
    if (diffSec >= 600) {
      c.state = 'offline';
      c.speedKmh = 0;
    }

    // Проверяем занятость аккаунта устройством
    c.isOccupied = Boolean(c.activeDeviceId);
  }
}

function sendJson(res, status, data) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

function readBody(req) {
  return new Promise((resolve) => {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        resolve({});
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    return sendJson(res, 200, { ok: true });
  }

  const url = new URL(req.url, `http://localhost:${PORT}`);
  const p = url.pathname;

  // 1. Все курьеры (с динамическим расчетом 10 минут отсутствия сигнала -> offline и флагом isOccupied)
  if (req.method === 'GET' && p === '/api/v1/couriers') {
    refreshCourierStatuses(db.couriers);
    return sendJson(res, 200, db.couriers);
  }

  // Сохранение/добавление курьера
  if (req.method === 'POST' && p === '/api/v1/couriers') {
    const body = await readBody(req);
    const idx = db.couriers.findIndex((c) => c.id === body.id);
    if (idx >= 0) db.couriers[idx] = { ...db.couriers[idx], ...body };
    else db.couriers.unshift(body);
    saveDb(db);
    return sendJson(res, 200, body);
  }

  // 2. Все заказы
  if (req.method === 'GET' && p === '/api/v1/orders') {
    return sendJson(res, 200, db.orders);
  }

  // 3. Приём заказов (Клиент + Госномер) по внешнему API
  if (req.method === 'POST' && p === '/api/v1/integration/incoming-orders') {
    const incoming = await readBody(req);
    const list = Array.isArray(incoming) ? incoming : [incoming];
    for (const item of list) {
      const exists = db.orders.some((o) => o.id === item.id);
      if (!exists) {
        db.orders.unshift(item);
      }
    }
    saveDb(db);
    return sendJson(res, 200, { status: 'SUCCESS', receivedCount: list.length, orders: list });
  }

  // 4. Персональная синхронизация аккаунта курьера: GET /api/v1/courier-app/:courierId/my-account
  const matchMyAccount = p.match(/^\/api\/v1\/courier-app\/([^/]+)\/my-account$/);
  if (req.method === 'GET' && matchMyAccount) {
    refreshCourierStatuses(db.couriers);
    const courierId = matchMyAccount[1];
    const courier = db.couriers.find((c) => c.id === courierId);
    if (!courier) return sendJson(res, 404, { error: 'Courier not found' });
    const myOrders = db.orders.filter((o) => o.courierId === courierId);
    return sendJson(res, 200, { courierAccount: courier, myOrders, syncedAtBishkek: nowBishkek() });
  }

  // 4.1 Вход курьера в аккаунт с проверкой на занятость: POST /api/v1/courier-app/:courierId/login
  const matchLogin = p.match(/^\/api\/v1\/courier-app\/([^/]+)\/login$/);
  if (req.method === 'POST' && matchLogin) {
    const courierId = matchLogin[1];
    const body = await readBody(req);
    const deviceId = body.deviceId || 'UNKNOWN';
    const deviceModel = body.deviceModel || 'Android Device';
    const force = Boolean(body.force);

    const courier = db.couriers.find((c) => c.id === courierId);
    if (!courier) return sendJson(res, 404, { success: false, error: 'Курьер не найден' });

    // Проверяем, занят ли аккаунт другим устройством
    if (courier.activeDeviceId && courier.activeDeviceId !== deviceId && !force) {
      return sendJson(res, 409, {
        success: false,
        occupied: true,
        message: `Аккаунт ${courierId} уже занят! В него выполнен вход на устройстве ${courier.activeDeviceModel || 'другого курьера'}. Одновременный вход запрещён.`,
        occupiedBy: courier.activeDeviceModel || 'Android'
      });
    }

    courier.activeDeviceId = deviceId;
    courier.activeDeviceModel = deviceModel;
    courier.isOccupied = true;
    courier.lastSignalTimestamp = Date.now();
    courier.lastSignalSec = 1;
    saveDb(db);
    return sendJson(res, 200, { success: true, courier });
  }

  // 4.2 Выход из аккаунта (освобождение экипажа): POST /api/v1/courier-app/:courierId/logout
  const matchLogout = p.match(/^\/api\/v1\/courier-app\/([^/]+)\/logout$/);
  if (req.method === 'POST' && matchLogout) {
    const courierId = matchLogout[1];
    const courier = db.couriers.find((c) => c.id === courierId);
    if (courier) {
      courier.activeDeviceId = null;
      courier.activeDeviceModel = null;
      courier.isOccupied = false;
      saveDb(db);
    }
    return sendJson(res, 200, { success: true });
  }

  // 5. Обновление персонального GPS и статуса курьера: POST /api/v1/courier-app/:courierId/gps
  const matchGps = p.match(/^\/api\/v1\/(?:courier-app|couriers)\/([^/]+)\/(?:gps|telemetry)$/);
  if (req.method === 'POST' && matchGps) {
    const courierId = matchGps[1];
    const payload = await readBody(req);
    const courier = db.couriers.find((c) => c.id === courierId);
    if (!courier) return sendJson(res, 404, { error: 'Courier not found' });
    courier.lat = Number(payload.lat ?? courier.lat);
    courier.lon = Number(payload.lon ?? courier.lon);
    courier.speedKmh = Number(payload.speedKmh ?? courier.speedKmh);
    courier.battery = Number(payload.battery ?? courier.battery);
    if (payload.state) courier.state = payload.state;
    if (payload.problemReason !== undefined) courier.problemReason = payload.problemReason;
    if (payload.idleMinutes !== undefined) courier.idleMinutes = Number(payload.idleMinutes);
    courier.lastSignalTimestamp = Date.now();
    courier.lastSignalSec = 1;
    saveDb(db);
    return sendJson(res, 200, courier);
  }

  // 5.1 Экстренный SOS сигнал: POST /api/v1/courier-app/:courierId/sos или /api/v1/couriers/:courierId/sos
  const matchSos = p.match(/^\/api\/v1\/(?:courier-app|couriers)\/([^/]+)\/sos$/);
  if (req.method === 'POST' && matchSos) {
    const courierId = matchSos[1];
    const payload = await readBody(req);
    const courier = db.couriers.find((c) => c.id === courierId);
    if (!courier) return sendJson(res, 404, { error: 'Courier not found' });
    courier.state = payload.state || 'problem';
    courier.problemReason = payload.reason || payload.problemReason || 'SOS сигнал курьера';
    courier.lastSignalTimestamp = Date.now();
    courier.lastSignalSec = 1;
    saveDb(db);
    return sendJson(res, 200, courier);
  }

  // 6. Отправка фотоотчёта доставки от курьера: POST /api/v1/courier-app/:courierId/orders/:orderId/deliver-photo-report
  const matchDeliver = p.match(/^\/api\/v1\/courier-app\/([^/]+)\/orders\/([^/]+)\/deliver-photo-report$/);
  if (req.method === 'POST' && matchDeliver) {
    const [, courierId, orderId] = matchDeliver;
    const payload = await readBody(req);
    const order = db.orders.find((o) => o.id === orderId);
    if (!order) return sendJson(res, 404, { error: 'Order not found' });

    const timeStr = nowBishkek();
    order.status = 'DELIVERED';
    order.proof = {
      type: 'Фотоотчёт + Код + Подпись',
      photoUrl: payload.photoDataUrl,
      code: payload.smsCode,
      signature: payload.signature || 'Подпись получена',
      at: timeStr,
      lat: payload.lat ?? order.lat,
      lon: payload.lon ?? order.lon
    };
    order.history = order.history || [];
    order.history.push({
      status: 'DELIVERED',
      at: timeStr,
      by: `Курьер ${courierId}`,
      coords: `${order.proof.lat}, ${order.proof.lon}`,
      comment: `Доставлено с фотоотчётом (код ${payload.smsCode})`
    });

    const courier = db.couriers.find((c) => c.id === courierId);
    if (courier) {
      courier.deliveredToday = (courier.deliveredToday || 0) + 1;
      courier.lat = Number(order.proof.lat);
      courier.lon = Number(order.proof.lon);
    }
    saveDb(db);
    return sendJson(res, 200, order);
  }

  // 7. Назначение и смена статуса заказа
  const matchAssign = p.match(/^\/api\/v1\/orders\/([^/]+)\/assign$/);
  if (req.method === 'POST' && matchAssign) {
    const orderId = matchAssign[1];
    const body = await readBody(req);
    const order = db.orders.find((o) => o.id === orderId);
    if (order) {
      order.courierId = body.courierId;
      order.status = 'ASSIGNED';
      saveDb(db);
    }
    return sendJson(res, 200, order || {});
  }

  const matchStatus = p.match(/^\/api\/v1\/orders\/([^/]+)\/status$/);
  if (req.method === 'POST' && matchStatus) {
    const orderId = matchStatus[1];
    const body = await readBody(req);
    const order = db.orders.find((o) => o.id === orderId);
    if (order) {
      order.status = body.status;
      saveDb(db);
    }
    return sendJson(res, 200, order || {});
  }

  return sendJson(res, 404, { error: 'Endpoint not found', path: p });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`================================================================`);
  console.log(`🚀 POST.KG REST API Server запущен на http://localhost:${PORT}/api/v1`);
  console.log(`📦 Файл базы данных: ${DB_FILE}`);
  console.log(`================================================================`);
});
