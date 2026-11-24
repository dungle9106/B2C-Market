#!/usr/bin/env node
// Cases for the churn and order fraud models (run after scripts/seed-demo.mjs): the demo buyers all keep buying and
// no order is ever refused at the door, so there is nothing for those models to learn. This adds, over ~7 months:
//
//   25 buyers who bought regularly, then stopped (some after a bad experience: 1-2 star review, cancelled order)
//   10 buyers who buy rarely (every 5-8 weeks) but keep coming back: "long since the last order" alone is not churn
//   18 "bom hang" accounts in 6 rings sharing a phone: new accounts, COD, big baskets, refused at the door
//   15 genuine new buyers whose first COD order arrived fine: a new account alone is not fraud
//
// Orders go through the real API (cart, checkout), then dates and outcomes are set in the database, like seed-demo.
// It also repairs the dates of an interrupted seed-demo run (accounts, shops, reviews still dated today).
//
//   node scripts/seed-risk-cases.mjs            (options as seed-demo: --docker, --api, --admin, --admin-password)
// Safe to run once: it stops if its accounts already exist.
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const opt = (name, def) => { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : def }
const DOCKER = args.includes('--docker')
const API = opt('--api', DOCKER ? 'http://localhost:3000/api' : 'http://localhost:8081/api').replace(/\/$/, '')
const ADMIN = opt('--admin', 'admin@marketplace.local')
const ADMIN_PW = opt('--admin-password', 'Admin@123')
const PASSWORD = 'Demo@1234'
const DOMAIN = 'shoppi.risk'
const log = (...m) => console.log(...m)

let seed = 4242
const rand = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296 }
const between = (a, b) => a + rand() * (b - a)
const int = (a, b) => Math.floor(between(a, b + 1))
const pick = (list) => list[Math.floor(rand() * list.length)]

async function call(method, p, token, body) {
  const r = await fetch(API + p, {
    method,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await r.text()
  let data = text
  try { data = JSON.parse(text) } catch { /* plain text */ }
  return { status: r.status, data }
}
async function ok(method, p, token, body) {
  const r = await call(method, p, token, body)
  if (r.status >= 300) throw new Error(`${method} ${p} -> ${r.status} ${JSON.stringify(r.data).slice(0, 300)}`)
  return r.data
}
function psql(sqlText) {
  const input = 'SET client_encoding = \'UTF8\';\n' + sqlText
  if (DOCKER) {
    return execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-v', 'ON_ERROR_STOP=1', '-q', '-U', 'postgres', '-d', 'marketplace', '-At'],
      { input, encoding: 'utf8', cwd: path.join(HERE, '..') })
  }
  let exe = process.env.PSQL || 'psql'
  const root = 'C:/Program Files/PostgreSQL'
  if (!process.env.PSQL && process.platform === 'win32' && fs.existsSync(root)) {
    const v = fs.readdirSync(root).sort((a, b) => Number(b) - Number(a)).find(v => fs.existsSync(`${root}/${v}/bin/psql.exe`))
    if (v) exe = `${root}/${v}/bin/psql.exe`
  }
  return execFileSync(exe, ['-v', 'ON_ERROR_STOP=1', '-q', '-At', '-h', process.env.PGHOST || '127.0.0.1', '-p', process.env.PGPORT || '5433',
    '-U', process.env.PGUSER || 'postgres', '-d', process.env.PGDATABASE || 'marketplace'],
  { input, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD || '123456', PGSSLMODE: process.env.PGSSLMODE || 'disable' } })
}
const q = (s) => `'${String(s).replace(/'/g, "''")}'`
const ts = (d) => `'${d.toISOString()}'::timestamptz AT TIME ZONE 'Asia/Ho_Chi_Minh'`
const hours = (d, h) => new Date(d.getTime() + h * 3600e3)
// a moment `days` days ago (fractional days allowed), local time between 8:00 and 22:59 unless an hour is given
function ago(days, hour) {
  const vnNow = new Date(Date.now() + 7 * 3600e3)
  const h = hour ?? int(8, 22)
  const whole = Math.floor(days)
  return new Date(Date.UTC(vnNow.getUTCFullYear(), vnNow.getUTCMonth(), vnNow.getUTCDate() - whole, h - 7, int(0, 59), int(0, 59)))
}

// ---------- 0. ready ----------
log(`Shoppi: ca rời bỏ & gian lận -> ${API}`)
const admin = (await ok('POST', '/auth/login', null, { email: ADMIN, password: ADMIN_PW })).token
if ((await call('POST', '/auth/login', null, { email: `newbie15@${DOMAIN}`, password: PASSWORD })).status === 200) {
  log('Các ca này đã được tạo trong database. Không tạo lại.')
  process.exit(0)
}

// repair an interrupted seed-demo: its accounts, shops and reviews must be older than its orders
log('0/4 Sửa ngày tháng của dữ liệu demo (nếu seed-demo bị dừng giữa chừng)...')
psql(`
update users u set created_at = (select min(o.created_at) from orders o where o.buyer_id = u.id) - interval '10 days'
  where u.email like '%@shoppi.demo' and exists (select 1 from orders o where o.buyer_id = u.id and o.created_at < u.created_at);
update users set created_at = now() - interval '135 days' where email like 'seller.%@shoppi.demo' and created_at > now() - interval '2 days';
update shops s set created_at = now() - interval '130 days' from users u where u.id = s.owner_id and u.email like 'seller.%@shoppi.demo' and s.created_at > now() - interval '2 days';
update products p set created_at = now() - interval '125 days' from shops s join users u on u.id = s.owner_id
  where p.shop_id = s.id and u.email like 'seller.%@shoppi.demo' and p.created_at > now() - interval '2 days';
update reviews r set created_at = o.delivered_at + (6 + random() * 60) * interval '1 hour'
  from order_items oi join orders o on o.id = oi.order_id
  where r.order_item_id = oi.id and o.delivered_at is not null and r.created_at > o.delivered_at + interval '4 days';
`.replace(/s\.owner_id/g, 's.' + shopOwnerColumn()))

function shopOwnerColumn() {
  return psql(`select column_name from information_schema.columns where table_name = 'shops' and column_name in ('owner_id', 'seller_id', 'user_id') limit 1;`).trim() || 'owner_id'
}

// products to order: simple ones (no variants), with stock, in a normal price range
const pool = psql(`
select p.id, p.price from products p join shops s on s.id = p.shop_id
where p.active and s.status = 'APPROVED' and p.stock > 40 and p.price between 40000 and 2500000
  and not exists (select 1 from product_variants v where v.product_id = p.id)
order by p.id;`).trim().split('\n').filter(Boolean).map(l => { const [id, price] = l.split('|'); return { id: Number(id), price: Number(price) } })
if (pool.length < 10) throw new Error('Không đủ sản phẩm để đặt hàng: hãy chạy scripts/seed-demo.mjs hoặc seed-real-products.mjs trước')
const cheap = pool.filter(p => p.price < 600000)
const pricey = pool.filter(p => p.price >= 600000)

const settingsBefore = (await ok('GET', '/admin/settings', admin)).registerPerIp
const restoreLimit = async () => { if (settingsBefore < 500) await call('PUT', '/admin/settings', admin, { registerPerIp: settingsBefore }) }
if (settingsBefore < 500) await ok('PUT', '/admin/settings', admin, { registerPerIp: 1000 })
process.on('uncaughtException', async (e) => { await restoreLimit(); console.error(e); process.exit(1) })

const FAMILY = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Phan', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương', 'Lý']
const GIVEN = ['Minh Anh', 'Gia Bảo', 'Khánh Linh', 'Hoàng Nam', 'Thảo Vy', 'Đức Huy', 'Ngọc Hân', 'Quốc Bảo', 'Mỹ Duyên', 'Trung Kiên', 'Thanh Hà', 'Văn Long', 'Thùy Dung', 'Tấn Phát', 'Kim Ngân']
const PLACES = [['TP. Hồ Chí Minh', 'Gò Vấp', 'Phường 5'], ['Hà Nội', 'Thanh Xuân', 'Khương Trung'], ['Đà Nẵng', 'Sơn Trà', 'An Hải Bắc'],
  ['Hải Phòng', 'Lê Chân', 'An Biên'], ['Đồng Nai', 'Biên Hòa', 'Tân Mai'], ['Nghệ An', 'Vinh', 'Hưng Bình']]
let person = 0
async function buyer(kind, n, phone) {
  const k = person++
  const name = `${FAMILY[k % FAMILY.length]} ${GIVEN[(k * 7) % GIVEN.length]}`
  const email = `${kind}${String(n).padStart(2, '0')}@${DOMAIN}`
  const own = `07${String(60000000 + k * 1234577).slice(0, 8)}`
  await call('POST', '/auth/register', null, { email, password: PASSWORD, fullName: name, phone: own })
  const token = (await ok('POST', '/auth/login', null, { email, password: PASSWORD })).token
  const [province, district, ward] = PLACES[k % PLACES.length]
  const addr = await ok('POST', '/buyer/addresses', token, { recipientName: name, phone: phone || own, addressLine: `${10 + k} Đường Hai Bà Trưng`, ward, district, province })
  const id = Number(psql(`select id from users where email = ${q(email)};`).trim())
  return { id, email, name, token, addressId: addr.id || (await ok('GET', '/buyer/addresses', token))[0].id }
}

const sql = []       // run once the API work is done
const failedStock = []
let orders = 0
// places one COD order through the API, returns its id; the outcome is set later in SQL
async function order(b, product, qty) {
  const item = await ok('POST', '/cart', b.token, { productId: product.id, quantity: qty })
  const placed = await ok('POST', '/orders/checkout', b.token, { shops: [{ cartItemIds: [item.id] }], addressId: b.addressId, paymentMethod: 'COD' })
  orders++
  return placed.orders[0].id
}
function delivered(orderId, created) {
  const confirmed = hours(created, between(1, 6))
  const shipped = hours(confirmed, between(8, 24))
  const done = hours(shipped, between(24, 70))
  sql.push(`update orders set created_at = ${ts(created)}, status = 'DELIVERED', confirmed_at = ${ts(confirmed)}, packed_at = ${ts(hours(confirmed, 2))},
    shipped_at = ${ts(shipped)}, delivered_at = ${ts(done)} where id = ${orderId};`,
  `update payments set status = 'PAID', paid_at = ${ts(done)} where order_id = ${orderId};`)
  return done
}
// "bom hang": shipped, the buyer refuses the parcel or never answers, it goes back to the shop
function refused(orderId, created, productId, qty) {
  const confirmed = hours(created, between(1, 5))
  const shipped = hours(confirmed, between(8, 20))
  const failed = hours(shipped, between(30, 60))
  sql.push(`update orders set created_at = ${ts(created)}, status = 'CANCELLED', confirmed_at = ${ts(confirmed)}, packed_at = ${ts(hours(confirmed, 2))},
    shipped_at = ${ts(shipped)}, cancelled_at = ${ts(hours(failed, 30))}, cancelled_by = 'SYSTEM',
    cancel_reason = 'Giao hàng không thành công: người nhận từ chối nhận hàng' where id = ${orderId};`,
  `update payments set status = 'CANCELLED' where order_id = ${orderId};`,
  `insert into tracking_events (order_id, stage, title, location, created_at) values (${orderId}, 'DELIVERY_FAILED', ${q(pick(['Người nhận từ chối nhận hàng', 'Không liên lạc được với người nhận', 'Người nhận hẹn giao lại nhiều lần rồi từ chối']))}, null, ${ts(failed)});`,
  `update products set stock = stock + ${qty} where id = ${productId};`)
}
// browsing and logins over a period (what the churn model sees in the last 30 days before each cut-off)
function activity(userId, fromDays, toDays, viewsPerWeek, loginsPerWeek) {
  const weeks = Math.max(0, (fromDays - toDays) / 7)
  const views = Math.round(weeks * viewsPerWeek * between(0.7, 1.3))
  const logins = Math.round(weeks * loginsPerWeek * between(0.7, 1.3))
  for (let k = 0; k < views; k++) sql.push(`insert into view_history (user_id, product_id, viewed_at) values (${userId}, ${pick(pool).id}, ${ts(ago(between(toDays, fromDays)))});`)
  for (let k = 0; k < logins; k++) sql.push(`insert into login_events (user_id, result, method, new_device, created_at) values (${userId}, 'SUCCESS', 'PASSWORD', false, ${ts(ago(between(toDays, fromDays)))});`)
}

// ---------- 1. buyers who leave ----------
log('1/4 25 người mua rời bỏ và 10 người mua thưa nhưng trung thành...')
const reviews = []   // [buyer, orderId, rating, comment, at]
for (let n = 1; n <= 25; n++) {
  const b = await buyer('churn', n)
  const start = int(150, 215)
  const gap = between(8, 24)
  const stop = int(68, 140)              // the day of the last order (days ago)
  const unhappy = rand() < 0.5
  sql.push(`update users set created_at = ${ts(ago(start + int(5, 30)))} where id = ${b.id};`)
  let last = null
  for (let d = start; d >= stop; d -= gap * between(0.7, 1.3)) {
    const product = pick(cheap)
    const id = await order(b, product, 1)
    last = { id, at: delivered(id, ago(d)) }
  }
  if (unhappy && last) reviews.push([b, last.id, pick([1, 1, 2]), pick(['Hàng kém chất lượng, không giống mô tả. Không mua nữa.', 'Giao chậm, đóng gói sơ sài, sản phẩm bị lỗi.', 'Thất vọng, shop không hỗ trợ đổi trả.']), hours(last.at, between(5, 40))])
  // they browsed while buying, a little less towards the end, then went quiet
  activity(b.id, start, stop + 10, 6, 2)
  activity(b.id, stop + 10, Math.max(0, stop - 15), unhappy ? 0.5 : 1.5, 0.5)
}
for (let n = 1; n <= 10; n++) {
  const b = await buyer('slow', n)
  const start = int(170, 215)
  const gap = between(35, 55)
  sql.push(`update users set created_at = ${ts(ago(start + int(5, 40)))} where id = ${b.id};`)
  for (let d = start; d >= 3; d -= gap * between(0.85, 1.15)) {
    const id = await order(b, pick(cheap), 1)
    delivered(id, ago(d))
  }
  activity(b.id, start, 0, 3, 1.5)
}
log(`   ${orders} đơn`)

// ---------- 2. "bom hang" rings and genuine new buyers ----------
log('2/4 18 tài khoản bom hàng (6 nhóm dùng chung SĐT) và 15 người mua mới thật...')
const before = orders
for (let ring = 0; ring < 6; ring++) {
  const phone = `03${String(91000000 + ring * 7654321).slice(0, 8)}`
  const startDay = int(8, 118)          // spread over the whole history: training, validation and test all have some
  for (let m = 0; m < 3; m++) {
    const b = await buyer('bom', ring * 3 + m + 1, phone)
    const born = ago(startDay - m * between(0.5, 4), int(0, 23))
    sql.push(`update users set created_at = ${ts(born)} where id = ${b.id};`,
      `insert into login_events (user_id, result, method, new_device, created_at) values (${b.id}, 'SUCCESS', 'PASSWORD', true, ${ts(hours(born, 0.2))});`)
    const n = int(1, 3)
    for (let k = 0; k < n; k++) {
      const product = pick(pricey.length ? pricey : pool)
      const qty = int(1, 3)
      const created = hours(born, between(0.3, 20) + k * between(1, 30))
      if (rand() < 0.4) created.setUTCHours(int(17, 21))   // 0h-4h Vietnam time
      refused(await order(b, product, qty), created, product.id, qty)
    }
  }
}
for (let n = 1; n <= 15; n++) {
  const b = await buyer('newbie', n)
  const born = ago(int(5, 118), int(7, 23))
  sql.push(`update users set created_at = ${ts(born)} where id = ${b.id};`,
    `insert into login_events (user_id, result, method, new_device, created_at) values (${b.id}, 'SUCCESS', 'PASSWORD', true, ${ts(hours(born, 0.1))});`)
  const product = pick(rand() < 0.3 && pricey.length ? pricey : cheap)
  delivered(await order(b, product, 1), hours(born, between(0.5, 30)))
}
log(`   ${orders - before} đơn`)

// ---------- 3. dates and outcomes ----------
log('3/4 Chuyển ngày tháng, đặt kết quả giao hàng...')
psql(sql.join('\n'))
let written = 0
for (const [b, orderId, rating, comment, at] of reviews) {
  const full = await ok('GET', `/orders/${orderId}`, b.token)
  const r = await call('POST', '/buyer/reviews', b.token, { orderItemId: full.items[0].id, rating, comment, shopRating: rating, shippingRating: 2 })
  if (r.status < 300) { psql(`update reviews set created_at = ${ts(at)} where id = ${r.data.id};`); written++ }
}
// the fraud scanner checked these orders with today's date: let it check them again with the real dates
psql(`delete from order_risks where verdict is null;`)
await restoreLimit()
log(`   ${written} đánh giá xấu`)

log(`4/4 Xong: ${orders} đơn. Vào trang admin "AI dự đoán rời bỏ" và "AI phát hiện gian lận đơn" rồi bấm Huấn luyện.
Tài khoản (mật khẩu ${PASSWORD}): churn01..25, slow01..10, bom01..18, newbie01..15 @${DOMAIN}`)
