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

  // 1. Все курьеры
  if (req.method === 'GET' && p === '/api/v1/couriers') {
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
    const courierId = matchMyAccount[1];
    const courier = db.couriers.find((c) => c.id === courierId);
    if (!courier) return sendJson(res, 404, { error: 'Courier not found' });
    const myOrders = db.orders.filter((o) => o.courierId === courierId);
    return sendJson(res, 200, { courierAccount: courier, myOrders, syncedAtBishkek: nowBishkek() });
  }

  // 5. Обновление персонального GPS курьера: POST /api/v1/courier-app/:courierId/gps
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

