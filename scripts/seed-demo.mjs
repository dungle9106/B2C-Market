#!/usr/bin/env node
// Shoppi demo data in one command: 4 shops, 24 products (with variants), 12 buyers and ~4 months of orders,
// reviews, returns, a flash sale and price watches - so every page (analytics, AI stock forecast, review hub,
// return-risk AI, Shoppi Xu, ...) has something to show.
//
// Everything goes through the real API (same rules as a person clicking), then the dates are moved back in the
// database so the history looks like four months of trading.
//
//   node scripts/seed-demo.mjs                 backend on http://localhost:8081, local PostgreSQL (psql)
//   node scripts/seed-demo.mjs --docker        the docker compose stack (http://localhost:3000, psql inside the db container)
//   options: --api <url>  --days <30..180>  --admin <email> --admin-password <pw>
//   psql (local mode) reads PGHOST, PGPORT (5433), PGUSER (postgres), PGPASSWORD (123456), PGDATABASE (marketplace), PSQL (path)
//
// Needs Node 18+. Safe to run once per database: it stops if the demo shops already exist.
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SHOPS, BUYERS, REVIEWS } from './demo-data/catalog.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const opt = (name, def) => { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : def }
const DOCKER = args.includes('--docker')
const API = opt('--api', DOCKER ? 'http://localhost:3000/api' : 'http://localhost:8081/api').replace(/\/$/, '')
const DAYS = Math.min(180, Math.max(30, Number(opt('--days', 120))))
const ADMIN = opt('--admin', 'admin@marketplace.local')
const ADMIN_PW = opt('--admin-password', 'Admin@123')
const PASSWORD = 'Demo@1234'
const DOMAIN = 'shoppi.demo'

// ---------- helpers ----------
let seed = 20251115
const rand = () => { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296 }   // repeatable
const pick = (list) => list[Math.floor(rand() * list.length)]
function poisson(lambda) {
  if (lambda <= 0) return 0
  const L = Math.exp(-lambda); let k = 0; let p = 1
  do { k++; p *= rand() } while (p > L)
  return k - 1
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms))
const log = (...m) => console.log(...m)

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
async function upload(token, file) {
  const form = new FormData()
  form.append('file', new Blob([fs.readFileSync(path.join(HERE, 'demo-data/images', file))], { type: 'image/jpeg' }), file)
  const r = await fetch(API + '/uploads', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
  if (r.status >= 300) throw new Error(`upload ${file} -> ${r.status} ${await r.text()}`)
  return (await r.json()).url
}

function psql(sqlText) {
  const input = 'SET client_encoding = \'UTF8\';\n' + sqlText
  if (DOCKER) {
    return execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-v', 'ON_ERROR_STOP=1', '-q', '-U', 'postgres', '-d', 'marketplace', '-At'],
      { input, encoding: 'utf8', cwd: path.join(HERE, '..') })
  }
  const exe = process.env.PSQL || findPsql()
  return execFileSync(exe, ['-v', 'ON_ERROR_STOP=1', '-q', '-At',
    '-h', process.env.PGHOST || '127.0.0.1', '-p', process.env.PGPORT || '5433',
    '-U', process.env.PGUSER || 'postgres', '-d', process.env.PGDATABASE || 'marketplace'],
  { input, encoding: 'utf8', env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD || '123456', PGSSLMODE: process.env.PGSSLMODE || 'disable' } })
}
function findPsql() {
  if (process.platform === 'win32') {
    const root = 'C:/Program Files/PostgreSQL'
    if (fs.existsSync(root)) {
      for (const v of fs.readdirSync(root).sort((a, b) => Number(b) - Number(a))) {
        const exe = `${root}/${v}/bin/psql.exe`
        if (fs.existsSync(exe)) return exe
      }
    }
  }
  return 'psql'
}
const q = (s) => `'${String(s).replace(/'/g, "''")}'`
const ts = (d) => `'${d.toISOString()}'::timestamptz AT TIME ZONE 'Asia/Ho_Chi_Minh'`
// a moment on the given day (days ago), local Vietnam time, between 8:00 and 22:59
function momentDaysAgo(daysAgo, hour = 8 + Math.floor(rand() * 15)) {
  const vnNow = new Date(Date.now() + 7 * 3600e3)
  const d = new Date(Date.UTC(vnNow.getUTCFullYear(), vnNow.getUTCMonth(), vnNow.getUTCDate() - daysAgo, hour - 7, Math.floor(rand() * 60), Math.floor(rand() * 60)))
  return d
}
const addHours = (d, h) => new Date(d.getTime() + h * 3600e3)

// ---------- 0. backend up, not seeded yet ----------
log(`Shoppi demo seed -> ${API} (${DAYS} ngày lịch sử)`)
for (let i = 0; ; i++) {
  try { if ((await fetch(API + '/categories')).ok) break } catch { /* not up yet */ }
  if (i === 0) log('Đang chờ backend khởi động...')
  if (i > 180) throw new Error('Không kết nối được backend tại ' + API)
  await sleep(1000)
}
try { psql('select 1;') } catch (e) {
  throw new Error('Không chạy được psql để chỉnh ngày tháng dữ liệu. ' + (DOCKER ? 'Hãy chạy trong thư mục có docker-compose.yml.' : 'Cài PostgreSQL client hoặc đặt biến PSQL/PG*.') + '\n' + e.message)
}
if ((await call('POST', '/auth/login', null, { email: `seller.${SHOPS[0].key}@${DOMAIN}`, password: PASSWORD })).status === 200) {
  log('Dữ liệu demo đã có sẵn trong database này (đăng nhập được tài khoản demo). Không tạo lại.')
  process.exit(0)
}
const admin = (await ok('POST', '/auth/login', null, { email: ADMIN, password: ADMIN_PW })).token
const categories = (await ok('GET', '/categories')).flatMap(c => [c, ...(c.children || [])])
const categoryId = (name) => (categories.find(c => c.name.toLowerCase().includes(name.toLowerCase())) || categories[0]).id

async function account(email, fullName, phone) {
  const r = await call('POST', '/auth/register', null, { email, password: PASSWORD, fullName, phone })
  if (r.status >= 300 && !(r.status === 400 || r.status === 409)) throw new Error(`register ${email}: ${r.status} ${JSON.stringify(r.data)}`)
  return (await ok('POST', '/auth/login', null, { email, password: PASSWORD })).token
}
const sqlLater = []   // statements run together once the API work is done

// ---------- 1. shops and products ----------
log('1/6 Tạo shop và sản phẩm...')
const skus = []        // { shop, product, productId, variantId, label, weight }
const products = {}    // key -> { id, shop, def, request, token }
for (const [si, s] of SHOPS.entries()) {
  const email = `seller.${s.key}@${DOMAIN}`
  const t = await account(email, `Chủ shop ${s.name}`, `09${String(10000000 + si * 1111111).slice(0, 8)}`)
  const applied = await ok('POST', '/seller/apply', t, {
    name: s.name, description: s.description, pickupAddress: `${12 + si} Đường Trần Hưng Đạo`, pickupPhone: `0909${String(100000 + si).slice(0, 6)}`,
    pickupDistrict: s.district, pickupWard: s.ward, province: s.province
  })
  const token = applied.token || t
  const shop = await ok('GET', '/seller/shop', token)
  if (shop.status && shop.status !== 'APPROVED') await ok('POST', `/admin/shops/${shop.id}/approve`, admin, {})
  const logo = await upload(token, `shop-${s.key}.jpg`)
  sqlLater.push(`update shops set logo_url = ${q(logo)} where id = ${shop.id};`)
  // demo shops count as having sent their papers (Mall needs a verified shop)
  psql(`update shops set verified = true where id = ${shop.id};`)
  if (s.mall) await ok('POST', `/admin/shops/${shop.id}/mall`, admin, { mall: true })
  s.id = shop.id
  s.token = token

  for (const p of s.products) {
    // stock: what the plan below will sell + what should be left at the end (set after planning)
    const images = [await upload(token, `${p.key}.jpg`), await upload(token, `${p.key}-2.jpg`)]
    p.images = images
    p.shop = s
  }
}

// ---------- 2. plan the demand, day by day ----------
log('2/6 Lập kế hoạch bán hàng theo ngày...')
const plan = []   // { daysAgo, shop, product, variantIndex, qty }
for (const s of SHOPS) {
  for (const p of s.products) {
    const variants = p.tiers ? cartesian(p.options) : [null]
    // some variants sell more (white tees, size M/L...)
    const weights = variants.map((_, i) => 1 + (variants.length - i) * 0.35 + rand() * 0.3)
    const wsum = weights.reduce((a, b) => a + b, 0)
    p.variantList = variants
    p.planned = variants.map(() => 0)
    for (let d = DAYS; d >= 1; d--) {
      const age = DAYS - d   // days since the start
      const weekday = momentDaysAgo(d, 12).getUTCDay()   // 0 Sunday, 6 Saturday
      const weekend = weekday === 0 || weekday === 6 ? p.demand.weekend : 1
      const lambda = Math.max(0, p.demand.base + p.demand.trend * age) * weekend * (0.85 + rand() * 0.3)
      let units = poisson(lambda)
      while (units > 0) {
        let r = rand() * wsum; let vi = 0
        while (r > weights[vi] && vi < weights.length - 1) { r -= weights[vi]; vi++ }
        const qty = Math.min(units, rand() < 0.75 ? 1 : 2)
        plan.push({ daysAgo: d, shop: s, product: p, variantIndex: vi, qty })
        p.planned[vi] += qty
        units -= qty
      }
    }
  }
}
function cartesian(lists) {
  return lists.reduce((acc, list) => acc.flatMap(a => list.map(v => [...a, v])), [[]])
}
log(`   ${plan.length} dòng hàng, ${plan.reduce((a, x) => a + x.qty, 0)} sản phẩm sẽ được bán`)

// create the products now that the stock is known
for (const s of SHOPS) {
  for (const p of s.products) {
    const end = p.demand.endStock
    const per = p.variantList.length
    const body = {
      name: p.name,
      description: `${p.name}. Hàng có sẵn, giao ngay trong ngày. Cam kết đúng mô tả, lỗi 1 đổi 1 trong ${7} ngày. ` +
        'Sản phẩm demo của Shoppi dùng để trình diễn các tính năng của sàn.',
      price: p.price, originalPrice: p.original || null, categoryId: categoryId(p.cat), imageUrls: p.images, returnDays: 7,
      freeShipping: !!p.freeShipping, sku: p.key.toUpperCase()
    }
    if (p.tiers) {
      body.tier1Name = p.tiers[0]
      if (p.tiers[1]) body.tier2Name = p.tiers[1]
      body.variants = p.variantList.map((v, i) => ({
        option1: v[0], option2: v[1] || null, name: v.join(' / '), sku: `${p.key.toUpperCase()}-${i + 1}`, extraPrice: 0,
        stock: p.planned[i] + Math.round(end / per) + (i === 0 ? end % per : 0)
      }))
    } else {
      body.stock = p.planned[0] + end
    }
    const created = await ok('POST', '/seller/products', s.token, body)
    if (created.moderationStatus && created.moderationStatus !== 'APPROVED') await ok('POST', `/admin/products/${created.id}/approve`, admin, {})
    const full = await ok('GET', `/products/${created.id}`)
    p.id = created.id
    p.request = body
    p.variantIds = p.tiers ? p.variantList.map(v => full.variants.find(x => x.option1 === v[0] && (x.option2 || null) === (v[1] || null))?.id) : [null]
    products[p.key] = p
  }
}

// ---------- 3. buyers ----------
log('3/6 Tạo người mua...')
const buyers = []
const FAMILY = ['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Vũ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương']
const GIVEN = ['Thị Mai', 'Văn Nam', 'Ngọc Ánh', 'Quang Huy', 'Thu Trang', 'Minh Khoa', 'Bảo Ngọc', 'Thanh Tùng', 'Hải Yến', 'Đức Anh', 'Phương Linh', 'Tuấn Kiệt']
const PLACES = [['TP. Hồ Chí Minh', 'Quận 10', 'Phường 12'], ['Hà Nội', 'Ba Đình', 'Kim Mã'], ['Đà Nẵng', 'Thanh Khê', 'Chính Gián'], ['Huế', 'Phú Nhuận', 'Vĩnh Ninh'], ['Hải Phòng', 'Ngô Quyền', 'Máy Tơ'], ['Cần Thơ', 'Cái Răng', 'Lê Bình']]
const people = [...BUYERS.slice(0, -1)]   // the last named buyer stays last: the one with the suspicious returns
for (let k = 0; people.length < 39; k++) people.push([`${FAMILY[k % FAMILY.length]} ${GIVEN[(k * 5) % GIVEN.length]}`, ...PLACES[k % PLACES.length]])
people.push(BUYERS[BUYERS.length - 1])
for (const [i, [name, province, district, ward]] of people.entries()) {
  const email = `buyer${String(i + 1).padStart(2, '0')}@${DOMAIN}`
  const phone = `09${String(31000000 + i * 1234567).slice(0, 8)}`
  const token = await account(email, name, phone)
  const addr = await ok('POST', '/buyer/addresses', token, { recipientName: name, phone, addressLine: `${20 + i} Đường Lê Lợi`, ward, district, province })
  buyers.push({ email, name, token, addressId: addr.id || (await ok('GET', '/buyer/addresses', token))[0].id, orders: [] })
}

// ---------- 4. orders, oldest first ----------
log('4/6 Đặt hàng (có thể mất vài phút)...')
// group a day's lines of one shop into orders of 1-3 lines
const orders = []
for (let d = DAYS; d >= 1; d--) {
  const today = plan.filter(x => x.daysAgo === d)
  for (const s of SHOPS) {
    const lines = today.filter(x => x.shop === s)
    while (lines.length) {
      const n = Math.min(lines.length, rand() < 0.7 ? 1 : rand() < 0.7 ? 2 : 3)
      orders.push({ daysAgo: d, shop: s, lines: lines.splice(0, n) })
    }
  }
}
// a handful placed today, still waiting for the shop
for (const s of SHOPS) {
  const p = s.products[Math.floor(rand() * 3)]
  orders.push({ daysAgo: 0, shop: s, lines: [{ product: p, variantIndex: 0, qty: 1, today: true }] })
}

let done = 0
let failed = 0
const t0 = Date.now()
for (let i = 0; i < orders.length;) {
  // a batch: different buyers in parallel (one buyer's cart is never used twice at once)
  const free = [...buyers].sort(() => rand() - 0.5)
  const batch = []
  while (i < orders.length && batch.length < Math.min(8, buyers.length)) {
    const o = orders[i++]
    o.buyer = free.pop()
    batch.push(o)
  }
  await Promise.all(batch.map(placeOrder))
  if (done % 100 < batch.length) process.stdout.write(`   ${done}/${orders.length} đơn\r`)
}
log(`   ${done} đơn đã đặt${failed ? `, ${failed} đơn lỗi (bỏ qua)` : ''} trong ${Math.round((Date.now() - t0) / 1000)}s`)

async function placeOrder(o) {
  try {
    const ids = []
    for (const l of o.lines) {
      const item = await ok('POST', '/cart', o.buyer.token, { productId: l.product.id, variantId: l.product.variantIds[l.variantIndex] || null, quantity: l.qty })
      ids.push(item.id)
    }
    const placed = await ok('POST', '/orders/checkout', o.buyer.token, { shops: [{ cartItemIds: ids }], addressId: o.buyer.addressId, paymentMethod: 'COD' })
    o.id = placed.orders[0].id
    o.buyer.orders.push(o)
    // ~5% of older orders get cancelled by the buyer (stock goes back like in real life)
    if (o.daysAgo >= 3 && rand() < 0.05) {
      await ok('PATCH', `/orders/${o.id}/cancel`, o.buyer.token, { reason: pick(['Đặt nhầm sản phẩm', 'Muốn đổi địa chỉ nhận hàng', 'Tìm được giá tốt hơn']) })
      o.cancelled = true
    }
    done++
  } catch (e) {
    failed++
    if (failed <= 5) console.warn('   ! ' + e.message)
  }
}

// ---------- 5. move the dates back, set the order states ----------
log('5/6 Chuyển ngày tháng về quá khứ...')
const stmts = []
for (const o of orders.filter(x => x.id)) {
  const created = o.daysAgo === 0 ? addHours(new Date(), -1 - rand() * 3) : momentDaysAgo(o.daysAgo)
  o.created = created
  if (o.cancelled) {
    stmts.push(`update orders set created_at = ${ts(created)}, cancelled_at = ${ts(addHours(created, 2))} where id = ${o.id};`)
    continue
  }
  let state
  if (o.daysAgo === 0) state = 'PENDING'
  else if (o.daysAgo === 1) state = pick(['PENDING', 'CONFIRMED', 'CONFIRMED', 'SHIPPING'])
  else if (o.daysAgo === 2) state = pick(['CONFIRMED', 'SHIPPING', 'SHIPPING'])
  else state = 'DELIVERED'
  const confirmed = addHours(created, 1 + rand() * 5)
  const shipped = addHours(confirmed, 8 + rand() * 16)
  const delivered = addHours(shipped, 24 + rand() * 48)
  // not arrived yet (placed 3-4 days ago, slow delivery): still on its way
  if (state === 'DELIVERED' && delivered > addHours(new Date(), -3)) state = 'SHIPPING'
  o.state = state
  o.delivered = delivered
  const set = [`created_at = ${ts(created)}`, `status = '${state}'`]
  if (state !== 'PENDING') set.push(`confirmed_at = ${ts(confirmed)}`)
  if (state === 'SHIPPING') set.push(`shipped_at = ${ts(shipped < new Date() ? shipped : addHours(new Date(), -2 - rand() * 4))}`)   // the tracking simulator carries on from here
  if (state === 'DELIVERED') set.push(`packed_at = ${ts(addHours(confirmed, 2))}`, `shipped_at = ${ts(shipped)}`, `delivered_at = ${ts(delivered)}`)
  stmts.push(`update orders set ${set.join(', ')} where id = ${o.id};`)
  if (state === 'DELIVERED') stmts.push(`update payments set status = 'PAID', paid_at = ${ts(delivered)} where order_id = ${o.id};`)
}
psql(stmts.join('\n') + '\n' + sqlLater.join('\n'))

// ---------- 6. reviews, returns, flash sale, price watches ----------
log('6/6 Đánh giá, trả hàng, Flash Sale, theo dõi giá...')
const delivered = orders.filter(o => o.state === 'DELIVERED')
const reviewDates = []
let reviews = 0
const ratingFor = (p) => {
  const r = rand()
  const bad = p.key === 'jeans' || p.key === 'lamp' ? 0.12 : 0   // a couple of products with more complaints
  if (r < 0.04 + bad) return 1
  if (r < 0.09 + bad * 1.6) return 2
  if (r < 0.17 + bad * 2) return 3
  if (r < 0.40) return 4
  return 5
}
await runPool(delivered.filter(() => rand() < 0.55), 8, async (o) => {
  const full = await ok('GET', `/orders/${o.id}`, o.buyer.token)
  for (const it of full.items) {
    const product = Object.values(products).find(p => p.id === it.productId)
    const rating = ratingFor(product)
    const r = await call('POST', '/buyer/reviews', o.buyer.token, {
      orderItemId: it.id, rating, comment: pick(REVIEWS[rating]),
      shopRating: Math.max(1, Math.min(5, rating + (rand() < 0.3 ? 1 : 0))), shippingRating: 4 + (rand() < 0.6 ? 1 : 0)
    })
    if (r.status < 300) { reviews++; reviewDates.push([r.data.id, addHours(o.delivered, 6 + rand() * 60)]) }
  }
})
log(`   ${reviews} đánh giá`)

// returns: two honest buyers, and one buyer who returns everything with the same photo and text (for the risk AI)
const recent = delivered.filter(o => o.daysAgo >= 3 && o.daysAgo <= 6)
const returnStmts = []
const fileReturn = async (o, description, evidence, reason = 'DAMAGED') => {
  const full = await ok('GET', `/orders/${o.id}`, o.buyer.token)
  return call('POST', `/orders/${o.id}/returns`, o.buyer.token, {
    type: 'REFUND_ONLY', reason, description, evidenceUrls: evidence,
    items: [{ orderItemId: full.items[0].id, quantity: 1 }]
  })
}
let returns = 0
for (const o of recent.slice(0, 2)) {
  const photo = await upload(o.buyer.token, `${o.lines[0].product.key}-2.jpg`)
  if ((await fileReturn(o, 'Sản phẩm bị trầy xước một góc khi mở hộp, mong shop hỗ trợ đổi hoặc hoàn tiền giúp mình.', [photo])).status < 300) returns++
}
const abuser = buyers[buyers.length - 1]
const abuserOrders = recent.filter(o => o.buyer === abuser)
const extra = []
for (let k = abuserOrders.length; k < 4; k++) {   // make sure this buyer has a few recent orders to return
  const p = pick(Object.values(products).filter(x => !x.tiers && x.demand.endStock > 20))
  const o = { daysAgo: 4, shop: p.shop, buyer: abuser, lines: [{ product: p, variantIndex: 0, qty: 1 }] }
  await placeOrder(o)
  if (o.id) {
    const created = momentDaysAgo(4 + k)
    psql(`update orders set status = 'DELIVERED', created_at = ${ts(created)}, confirmed_at = ${ts(addHours(created, 2))}, shipped_at = ${ts(addHours(created, 20))}, delivered_at = ${ts(addHours(created, 50))} where id = ${o.id};
          update payments set status = 'PAID', paid_at = ${ts(addHours(created, 50))} where order_id = ${o.id};`)
    extra.push(o)
  }
}
const abuserPhoto = await upload(abuser.token, 'cable-2.jpg')
for (const o of [...abuserOrders, ...extra].slice(0, 4)) {
  if ((await fileReturn(o, 'Hàng nhận được bị hỏng hoàn toàn không sử dụng được, yêu cầu shop hoàn tiền ngay.', [abuserPhoto])).status < 300) returns++
}
log(`   ${returns} yêu cầu trả hàng (1 người mua có dấu hiệu lạm dụng)`)

// price watches first, then a flash sale on two of the watched products: the bell rings within ~5 minutes
const demoBuyer = buyers[0]
for (const key of ['powerbank', 'sunscreen', 'keyboard', 'airfryer']) {
  await call('PUT', `/buyer/price-watches/${products[key].id}`, demoBuyer.token, key === 'keyboard' ? { targetPrice: 650000 } : {})
}
const flashStart = new Date(Date.now() - 3600e3)
const flashEnd = new Date(Date.now() + 3 * 86400e3)
const local = (d) => new Date(d.getTime() + 7 * 3600e3).toISOString().slice(0, 19)
for (const [key, pct] of [['powerbank', 0.2], ['sunscreen', 0.25]]) {
  const p = products[key]
  const current = await ok('GET', `/products/${p.id}`)
  await ok('PUT', `/seller/products/${p.id}`, p.shop.token, {
    ...p.request, stock: current.stock,
    flashSalePrice: Math.round(p.price * (1 - pct) / 1000) * 1000, flashSaleStart: local(flashStart), flashSaleEnd: local(flashEnd), flashSaleStock: 40, flashSaleMaxPerBuyer: 2
  })
}
// a few searches for "Tim kiem hang dau" / hot keywords
for (const [kw, n] of [['áo thun', 9], ['tai nghe bluetooth', 7], ['kem chống nắng', 6], ['bình giữ nhiệt', 5], ['sạc dự phòng', 4], ['son kem', 3]]) {
  for (let k = 0; k < n; k++) await call('GET', `/products/search?q=${encodeURIComponent(kw)}`, pick(buyers).token)
}

// review dates, and what stays consistent with moved dates
const last = [
  ...reviewDates.map(([id, d]) => `update reviews set created_at = ${ts(d)} where id = ${id};`),
  // the bluetooth earbuds have just sold out (the forecast page shows "Het hang")
  `update products set stock = 0 where id = ${products.earbuds.id};`,
  // products and shops look established
  `update products set created_at = now() - interval '${DAYS + 5} days' where shop_id in (${SHOPS.map(s => s.id).join(',')});`,
  `update shops set created_at = now() - interval '${DAYS + 10} days' where id in (${SHOPS.map(s => s.id).join(',')});`,
  `update users set created_at = now() - interval '${DAYS + 10} days' where email like '%@${DOMAIN}';`
]
try { psql(last.join('\n')) } catch (e) { console.warn('   ! chỉnh ngày đánh giá/shop: ' + e.message.split('\n')[0]) }

log(`
Xong! Tài khoản demo (mật khẩu: ${PASSWORD})
  Người mua : ${buyers[0].email}  (đang theo dõi giá 4 sản phẩm, có Flash Sale vừa bắt đầu)
              buyer02..buyer${buyers.length}@${DOMAIN}  (buyer${buyers.length}: có dấu hiệu lạm dụng trả hàng)
  Người bán : ${SHOPS.map(s => `seller.${s.key}@${DOMAIN}`).join(', ')}
              -> Kênh Người Bán > Dự Báo Nhập Hàng (AI), Phân tích doanh thu, Trung tâm đánh giá
  Quản trị  : ${ADMIN}
              -> Phát hiện lạm dụng đổi trả, Đối soát, Báo cáo
`)

async function runPool(list, size, fn) {
  let k = 0
  await Promise.all(Array.from({ length: size }, async () => {
    while (k < list.length) {
      const item = list[k++]
      try { await fn(item) } catch (e) { console.warn('   ! ' + e.message) }
    }
  }))
}
