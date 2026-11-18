#!/usr/bin/env node
// ~180 real products with real photos, in 7 new shops: beauty, tech, kitchen, groceries, fashion, watches, sport.
// Photos and specs come from DummyJSON (https://dummyjson.com, free sample product data); names are translated to
// Vietnamese (scripts/demo-data/real-products.mjs), prices converted to VND. Every photo is downloaded and uploaded
// to Shoppi (/api/uploads), so the products do not depend on the outside site afterwards.
//
//   node scripts/seed-real-products.mjs                       backend on http://localhost:8081
//   node scripts/seed-real-products.mjs --docker              the docker compose stack (http://localhost:3000)
//   options: --api <url>  --admin <email> --admin-password <pw>  --limit <n> (only the first n products, to try it)
//
// Safe to run again: products a shop already has (same name) are skipped, so an interrupted run just continues.
// Seller accounts: seller.<shop>@shoppi.real, password Demo@1234.
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import { PRODUCTS, SHOPS, SHOP_OF, CATEGORY_NAMES } from './demo-data/real-products.mjs'

const args = process.argv.slice(2)
const opt = (name, def) => { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : def }
const API = opt('--api', args.includes('--docker') ? 'http://localhost:3000/api' : 'http://localhost:8081/api').replace(/\/$/, '')
const ADMIN = opt('--admin', 'admin@marketplace.local')
const ADMIN_PW = opt('--admin-password', 'Admin@123')
const LIMIT = Number(opt('--limit', 0)) || PRODUCTS.length
const SOURCE = 'https://dummyjson.com/products?limit=0'
const PASSWORD = 'Demo@1234'
const DOMAIN = 'shoppi.real'
const USD = 25000   // VND per USD
const log = (...m) => console.log(...m)
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

// Shoppi Mall needs a verified shop (papers checked by an admin): the "chinh hang" shops are marked verified in the
// database, like scripts/seed-demo.mjs does. Best effort: without psql they are simply normal shops.
function markVerified(shopId) {
  try {
    if (args.includes('--docker')) {
      execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-q', '-U', 'postgres', '-d', 'marketplace', '-c', `update shops set verified = true where id = ${shopId}`])
    } else {
      let exe = process.env.PSQL || 'psql'
      const root = 'C:/Program Files/PostgreSQL'
      if (!process.env.PSQL && process.platform === 'win32' && fs.existsSync(root)) {
        const v = fs.readdirSync(root).sort((a, b) => Number(b) - Number(a)).find(v => fs.existsSync(`${root}/${v}/bin/psql.exe`))
        if (v) exe = `${root}/${v}/bin/psql.exe`
      }
      execFileSync(exe, ['-q', '-h', process.env.PGHOST || '127.0.0.1', '-p', process.env.PGPORT || '5433', '-U', process.env.PGUSER || 'postgres',
        '-d', process.env.PGDATABASE || 'marketplace', '-c', `update shops set verified = true where id = ${shopId}`],
      { env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD || '123456' }, stdio: 'ignore' })
    }
    return true
  } catch {
    return false
  }
}

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
async function retry(what, fn) {
  for (let i = 1; ; i++) {
    try { return await fn() } catch (e) {
      if (i >= 3) throw new Error(`${what}: ${e.message}`)
      await sleep(1000 * i)
    }
  }
}

// a photo from the source -> uploaded to Shoppi, returns its Shoppi url
async function copyImage(token, url) {
  return retry('ảnh ' + url, async () => {
    const r = await fetch(url, { signal: AbortSignal.timeout(30000) })
    if (!r.ok) throw new Error('HTTP ' + r.status)
    const bytes = Buffer.from(await r.arrayBuffer())
    const ext = (url.split('?')[0].split('.').pop() || 'jpg').toLowerCase()
    const type = { webp: 'image/webp', png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg' }[ext] || 'image/jpeg'
    const form = new FormData()
    form.append('file', new Blob([bytes], { type }), `product.${ext}`)
    const up = await fetch(API + '/uploads', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
    if (up.status >= 300) throw new Error(`upload -> ${up.status} ${await up.text()}`)
    return (await up.json()).url
  })
}

// prices like a Vietnamese shop: 249.000đ, not 249.750đ
const vnd = (usd) => Math.max(9000, Math.round(usd * USD / 1000) * 1000 - 1000)

const WARRANTY = [[/lifetime/i, 'Bảo hành trọn đời'], [/no warranty/i, 'Không bảo hành'], [/(\d+)\s*year/i, (m) => `Bảo hành ${m[1]} năm`],
  [/(\d+)\s*month/i, (m) => `Bảo hành ${m[1]} tháng`], [/(\d+)\s*week/i, (m) => `Bảo hành ${m[1]} tuần`]]
function translate(text, rules) {
  for (const [re, out] of rules) {
    const m = String(text || '').match(re)
    if (m) return typeof out === 'function' ? out(m) : out
  }
  return null
}
const returnDays = (policy) => /no return/i.test(policy || '') ? 0 : Math.min(30, Number((policy || '').match(/(\d+)\s*day/i)?.[1] || 7))

function description(name, x, days) {
  const lines = [`${name}${x.brand ? ` - thương hiệu ${x.brand}` : ''}.`, '']
  lines.push('THÔNG TIN SẢN PHẨM')
  if (x.brand) lines.push(`- Thương hiệu: ${x.brand}`)
  if (x.sku) lines.push(`- Mã sản phẩm: ${x.sku}`)
  if (x.weight) lines.push(`- Trọng lượng: ${x.weight} ${x.weight > 50 ? 'g' : 'kg'}`)
  if (x.dimensions) lines.push(`- Kích thước: ${x.dimensions.width} x ${x.dimensions.height} x ${x.dimensions.depth} cm`)
  const warranty = translate(x.warrantyInformation, WARRANTY)
  if (warranty) lines.push(`- ${warranty}`)
  if (x.minimumOrderQuantity > 1) lines.push(`- Đơn sỉ từ ${x.minimumOrderQuantity} sản phẩm được giá tốt hơn, nhắn shop để báo giá`)
  lines.push('', 'CAM KẾT CỦA SHOP')
  lines.push('- Hàng đúng mô tả, hình ảnh chụp sản phẩm thật')
  lines.push(days > 0 ? `- Đổi trả trong ${days} ngày nếu sản phẩm lỗi hoặc không đúng mô tả` : '- Sản phẩm không áp dụng đổi trả (trừ lỗi nhà sản xuất)')
  lines.push('- Đóng gói cẩn thận, giao hàng toàn quốc')
  if (x.tags?.length) lines.push('', x.tags.map(t => '#' + t.replace(/\s+/g, '')).join(' '))
  return lines.join('\n')
}

// ---------- 0. backend, admin, source data ----------
log(`Shoppi: đăng sản phẩm thật -> ${API}`)
for (let i = 0; ; i++) {
  try { if ((await fetch(API + '/categories')).ok) break } catch { /* not up yet */ }
  if (i === 0) log('Đang chờ backend khởi động...')
  if (i > 180) throw new Error('Không kết nối được backend tại ' + API)
  await sleep(1000)
}
const admin = (await ok('POST', '/auth/login', null, { email: ADMIN, password: ADMIN_PW })).token
log('Tải danh sách sản phẩm nguồn (DummyJSON)...')
const source = new Map((await retry('DummyJSON', async () => {
  const r = await fetch(SOURCE, { signal: AbortSignal.timeout(30000) })
  if (!r.ok) throw new Error('HTTP ' + r.status)
  return r.json()
})).products.map(x => [x.id, x]))

const tree = await ok('GET', '/categories/tree')
const flat = []
const walk = (list) => list.forEach(c => { flat.push(c); walk(c.children || []) })
walk(tree)
const categoryId = (id) => (flat.find(c => c.name === CATEGORY_NAMES[id]) || flat.find(c => c.id === id) || flat[0]).id

// the platform allows only so many new accounts per address and 10 minutes: raise it while the shops are created
const settingsBefore = (await ok('GET', '/admin/settings', admin)).registerPerIp
const restoreLimit = async () => { if (settingsBefore < 200) await call('PUT', '/admin/settings', admin, { registerPerIp: settingsBefore }) }
if (settingsBefore < 200) await ok('PUT', '/admin/settings', admin, { registerPerIp: 1000 })
process.on('uncaughtException', async (e) => { await restoreLimit(); console.error(e); process.exit(1) })

// ---------- 1. shops ----------
log('1/2 Tạo 7 shop...')
const shops = {}
try {
  for (const [si, s] of SHOPS.entries()) {
    const email = `seller.${s.key}@${DOMAIN}`
    const r = await call('POST', '/auth/register', null, { email, password: PASSWORD, fullName: `Chủ shop ${s.name}`, phone: `08${String(81000000 + si * 1234567).slice(0, 8)}` })
    if (r.status >= 300 && r.status !== 400 && r.status !== 409) throw new Error(`register ${email}: ${r.status} ${JSON.stringify(r.data)}`)
    let token = (await ok('POST', '/auth/login', null, { email, password: PASSWORD })).token
    let shop = await call('GET', '/seller/shop', token)
    if (shop.status >= 300) {
      const applied = await ok('POST', '/seller/apply', token, {
        name: s.name, description: s.description, pickupAddress: `${30 + si} Đường Nguyễn Huệ`, pickupPhone: `0908${String(200000 + si).slice(0, 6)}`,
        pickupDistrict: s.district, pickupWard: s.ward, province: s.province
      })
      token = applied.token || token
      shop = await call('GET', '/seller/shop', token)
    }
    if (shop.data.status && shop.data.status !== 'APPROVED') await ok('POST', `/admin/shops/${shop.data.id}/approve`, admin, {})
    // after approval the account is a seller: log in again for a token with that role
    token = (await ok('POST', '/auth/login', null, { email, password: PASSWORD })).token
    if (s.mall && !shop.data.mall && markVerified(shop.data.id)) {
      const m = await call('POST', `/admin/shops/${shop.data.id}/mall`, admin, { mall: true })
      if (m.status >= 300) log(`   (không bật được Shoppi Mall cho ${s.name}: ${JSON.stringify(m.data).slice(0, 120)})`)
    }
    const existing = new Set((await ok('GET', '/seller/products', token)).map(p => p.name))
    shops[s.key] = { ...s, id: shop.data.id, token, existing }
    log(`   ${s.name}: đã có ${existing.size} sản phẩm`)
  }
} finally {
  await restoreLimit()
}

// ---------- 2. products ----------
log('2/2 Đăng sản phẩm (tải ảnh thật rồi đăng lên Shoppi)...')
let created = 0, skipped = 0, failed = 0, images = 0
const todo = PRODUCTS.slice(0, LIMIT)
for (const [i, [id, name, cat, variants]] of todo.entries()) {
  const x = source.get(id)
  const shop = x && shops[SHOP_OF[x.category]]
  if (!x || !shop) { log(`   bỏ qua #${id}: không có trong nguồn`); failed++; continue }
  if (shop.existing.has(name)) { skipped++; continue }
  try {
    const urls = []
    for (const u of (x.images?.length ? x.images : [x.thumbnail]).slice(0, 9)) urls.push(await copyImage(shop.token, u))
    images += urls.length
    const price = vnd(x.price)
    const original = x.discountPercentage >= 3 ? Math.round(price / (1 - x.discountPercentage / 100) / 1000) * 1000 : null
    const days = returnDays(x.returnPolicy)
    const stock = Math.max(12, x.stock || 0)
    const body = {
      name: name.slice(0, 120), description: description(name, x, days).slice(0, 4000), price, originalPrice: original,
      categoryId: categoryId(cat), imageUrls: urls, returnDays: days, freeShipping: price >= 500000, sku: (x.sku || `DJ-${id}`).slice(0, 50)
    }
    if (variants) {
      const [tier, options] = variants
      body.tier1Name = tier
      body.variants = options.map((o, k) => ({
        option1: o, name: o, sku: `${body.sku}-${k + 1}`.slice(0, 50),
        extraPrice: tier === 'Dung lượng' ? k * Math.round(price * 0.15 / 1000) * 1000 : 0,
        stock: Math.max(3, Math.round(stock / options.length))
      }))
    } else {
      body.stock = stock
    }
    const p = await ok('POST', '/seller/products', shop.token, body)
    if (p.moderationStatus && p.moderationStatus !== 'APPROVED') await call('POST', `/admin/products/${p.id}/approve`, admin, {})
    shop.existing.add(name)
    created++
    log(`   [${i + 1}/${todo.length}] ${name} - ${price.toLocaleString('vi-VN')}đ, ${urls.length} ảnh (${shop.name})`)
  } catch (e) {
    failed++
    log(`   [${i + 1}/${todo.length}] LỖI ${name}: ${e.message}`)
  }
}

log(`\nXong: đăng mới ${created} sản phẩm (${images} ảnh), ${skipped} đã có sẵn, ${failed} lỗi.`)
log(`Tài khoản người bán: seller.<beauty|tech|kitchen|grocery|fashion|watch|sport>@${DOMAIN} / ${PASSWORD}`)
