#!/usr/bin/env node
// Integration tests through the real API (a running backend + its database): the money and stock rules a marketplace
// must never get wrong. Every run uses new accounts, so it can run on any database (dev, demo, docker).
//
//   node scripts/test-e2e.mjs                       backend on http://localhost:8081
//   node scripts/test-e2e.mjs --api http://localhost:3000/api --admin admin@marketplace.local --admin-password Admin@123
//
// Exit code 0 when every check passes.
const args = process.argv.slice(2)
const opt = (name, def) => { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : def }
const API = opt('--api', 'http://localhost:8081/api').replace(/\/$/, '')
const ADMIN = opt('--admin', 'admin@marketplace.local')
const ADMIN_PW = opt('--admin-password', 'Admin@123')
const RUN = Date.now().toString(36)
const PASSWORD = 'Test@12345'

let passed = 0
let failed = 0
let group = ''
function check(name, ok, detail = '') {
  if (ok) passed++
  else failed++
  console.log(`  ${ok ? '✓' : '✗'} ${name}${!ok && detail ? '  -> ' + detail : ''}`)
}
const section = (name) => { group = name; console.log('\n' + name) }
const num = (v) => Number(v)

async function call(method, path, token, body) {
  const r = await fetch(API + path, {
    method,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await r.text()
  let data = text
  try { data = JSON.parse(text) } catch { /* plain text */ }
  return { status: r.status, data }
}
async function ok(method, path, token, body) {
  const r = await call(method, path, token, body)
  if (r.status >= 300) throw new Error(`[${group}] ${method} ${path} -> ${r.status} ${JSON.stringify(r.data).slice(0, 300)}`)
  return r.data
}
// the platform allows only so many new accounts per address and 10 minutes (anti-spam, admin setting): raise it while
// this script creates its accounts, and always put the admin's value back
async function relaxRegisterLimit(adminToken, needed) {
  const before = (await ok('GET', '/admin/settings', adminToken)).registerPerIp
  if (before >= needed) return async () => {}
  await ok('PUT', '/admin/settings', adminToken, { registerPerIp: 1000 })
  return async () => { await call('PUT', '/admin/settings', adminToken, { registerPerIp: before }) }
}

async function account(tag) {
  const email = `e2e-${tag}-${RUN}@test.local`
  await ok('POST', '/auth/register', null, { email, password: PASSWORD, fullName: `Test ${tag}`, phone: '0912345678' })
  return { email, token: (await ok('POST', '/auth/login', null, { email, password: PASSWORD })).token }
}
async function buyer(tag) {
  const b = await account(tag)
  const a = await ok('POST', '/buyer/addresses', b.token, { recipientName: `Test ${tag}`, phone: '0912345678', addressLine: '1 Lê Lợi', ward: 'Bến Nghé', district: 'Quận 1', province: 'TP. Hồ Chí Minh' })
  b.addressId = a.id || (await ok('GET', '/buyer/addresses', b.token))[0].id
  return b
}
async function seller(tag, admin) {
  const s = await account(tag)
  const applied = await ok('POST', '/seller/apply', s.token, { name: `Shop ${tag} ${RUN}`, description: 'Shop kiểm thử tự động', pickupAddress: '1 Lê Lợi', pickupPhone: '0900000000', pickupDistrict: 'Quận 1', pickupWard: 'Bến Nghé', province: 'TP. Hồ Chí Minh' })
  s.token = applied.token || s.token
  const shop = await ok('GET', '/seller/shop', s.token)
  if (shop.status && shop.status !== 'APPROVED') await ok('POST', `/admin/shops/${shop.id}/approve`, admin, {})
  s.shopId = shop.id
  return s
}
async function product(s, admin, { name, price, stock }) {
  const categories = (await ok('GET', '/categories')).flatMap(c => [c, ...(c.children || [])])
  const p = await ok('POST', '/seller/products', s.token, {
    name: `${name} ${RUN}`, description: 'Sản phẩm dùng cho kiểm thử tự động, mô tả đủ dài để được đăng bán.',
    price, stock, categoryId: categories[0].id, imageUrls: ['/uploads/test.jpg'], returnDays: 7
  })
  if (p.moderationStatus && p.moderationStatus !== 'APPROVED') await ok('POST', `/admin/products/${p.id}/approve`, admin, {})
  return p
}
const stockOf = async (id) => (await ok('GET', `/products/${id}`)).stock
async function place(b, items, extra = {}) {
  const ids = []
  for (const [p, qty] of items) ids.push((await ok('POST', '/cart', b.token, { productId: p.id, quantity: qty })).id)
  const byShop = {}
  for (const [k, [p]] of items.entries()) (byShop[p.shopId] ||= []).push(ids[k])
  const body = { shops: Object.values(byShop).map(cartItemIds => ({ cartItemIds })), addressId: b.addressId, paymentMethod: 'COD', ...extra }
  const preview = await ok('POST', '/orders/preview', b.token, body)
  const placed = await call('POST', '/orders/checkout', b.token, body)
  return { preview, placed, ids }
}
async function deliver(s, orderId) {
  for (const st of ['CONFIRMED', 'SHIPPING', 'DELIVERED']) await ok('PATCH', `/seller/orders/${orderId}/status`, s.token, { status: st })
}

// ======================================================================
for (let i = 0; ; i++) {
  try { if ((await fetch(API + '/categories')).ok) break } catch { /* not up */ }
  if (i > 120) { console.error('Backend không chạy tại ' + API); process.exit(2) }
  await new Promise(r => setTimeout(r, 1000))
}
console.log(`Kiểm thử tích hợp Shoppi -> ${API}`)
// a fresh backend answers before its first-run setup has created the admin: try for a while
let admin
for (let i = 0; !admin; i++) {
  const r = await call('POST', '/auth/login', null, { email: ADMIN, password: ADMIN_PW })
  if (r.status === 200) admin = r.data.token
  else if (i > 30) { console.error('Không đăng nhập được admin ' + ADMIN); process.exit(2) }
  else await new Promise(res => setTimeout(res, 1000))
}
const restoreLimit = await relaxRegisterLimit(admin, 200)
try {
const shopA = await seller('shopa', admin)
const shopB = await seller('shopb', admin)
const shirt = await product(shopA, admin, { name: 'Áo kiểm thử', price: 200000, stock: 50 })
const cap = await product(shopB, admin, { name: 'Mũ kiểm thử', price: 150000, stock: 50 })
const last = await product(shopA, admin, { name: 'Hàng chỉ còn 3', price: 100000, stock: 3 })
shirt.shopId = shopA.shopId; cap.shopId = shopB.shopId; last.shopId = shopA.shopId

// ----------------------------------------------------------------------
section('1. Tính tiền khi đặt hàng')
{
  const b = await buyer('pricing')
  const before = await stockOf(shirt.id)
  const { preview, placed } = await place(b, [[shirt, 2]], { platformVoucherCode: 'SHOPPI10' })
  const o = placed.data.orders?.[0]
  check('đặt hàng thành công', placed.status === 200 && !!o, JSON.stringify(placed.data).slice(0, 200))
  check('tiền hàng = 2 x 200.000', num(o.subtotal) === 400000, o.subtotal)
  check('voucher SHOPPI10: 10% nhưng tối đa 30.000', num(o.platformDiscount) === 30000, o.platformDiscount)
  check('tổng = tiền hàng - giảm giá + phí ship - giảm phí ship',
    num(o.total) === num(o.subtotal) - num(o.discount) - num(o.coinDiscount || 0) - num(o.comboDiscount || 0) + num(o.shippingFee) - num(o.shippingDiscount),
    JSON.stringify({ total: o.total, subtotal: o.subtotal, discount: o.discount, ship: o.shippingFee, shipDisc: o.shippingDiscount }))
  check('số tiền khi đặt khớp với báo giá ở bước xem trước', num(o.total) === num(preview.total), `${o.total} vs ${preview.total}`)
  check('Xu sẽ nhận = 1% tiền thực trả cho hàng (làm tròn xuống)', o.coinsToEarn === Math.floor((400000 - 30000) / 100), o.coinsToEarn)
  check('kho giảm đúng 2', (await stockOf(shirt.id)) === before - 2)
}

// ----------------------------------------------------------------------
section('2. Đơn nhiều shop: chia voucher sàn')
{
  const b = await buyer('multishop')
  const { preview, placed } = await place(b, [[shirt, 1], [cap, 1]], { platformVoucherCode: 'SHOPPI10' })
  const orders = placed.data.orders || []
  check('tách thành 2 đơn, mỗi shop 1 đơn', orders.length === 2 && new Set(orders.map(o => o.shopId)).size === 2)
  const share = orders.reduce((a, o) => a + num(o.platformDiscount), 0)
  check('phần voucher chia cho các shop cộng lại đúng bằng voucher', share === num(preview.platformDiscountTotal) && share === 30000, `${share}`)
  check('shop bán nhiều tiền hơn gánh phần lớn hơn', num(orders.find(o => o.shopId === shopA.shopId).platformDiscount) > num(orders.find(o => o.shopId === shopB.shopId).platformDiscount))
  const total = orders.reduce((a, o) => a + num(o.total), 0)
  check('tổng các đơn = tổng báo giá', total === num(preview.total), `${total} vs ${preview.total}`)
}

// ----------------------------------------------------------------------
section('3. Hủy đơn: trả lại kho và Xu')
{
  const b = await buyer('cancel')
  await ok('POST', '/buyer/coins/check-in', b.token)
  const coins0 = (await ok('GET', '/buyer/coins', b.token)).balance
  const before = await stockOf(shirt.id)
  const { placed } = await place(b, [[shirt, 1]], { useCoins: true })
  const o = placed.data.orders[0]
  check('dùng Xu đã điểm danh để giảm tiền', num(o.coinDiscount) === coins0 && coins0 > 0, `${o.coinDiscount} / ${coins0}`)
  check('số dư Xu bị trừ', (await ok('GET', '/buyer/coins', b.token)).balance === 0)
  await ok('PATCH', `/orders/${o.id}/cancel`, b.token, { reason: 'Đặt nhầm' })
  check('hủy xong: kho trở lại như cũ', (await stockOf(shirt.id)) === before)
  check('hủy xong: Xu được hoàn', (await ok('GET', '/buyer/coins', b.token)).balance === coins0)
  const again = await call('PATCH', `/orders/${o.id}/cancel`, b.token, { reason: 'lần 2' })
  check('không hủy được 2 lần', again.status >= 400 && again.status < 500, again.status)
}

// ----------------------------------------------------------------------
section('4. Ví ShoppiPay')
{
  const b = await buyer('wallet')
  await ok('POST', '/buyer/wallet/top-up', b.token, { amount: 500000 })
  check('nạp 500.000', num((await ok('GET', '/buyer/wallet', b.token)).balance) === 500000)
  const { placed } = await place(b, [[shirt, 1]], { paymentMethod: 'SHOPPI_PAY' })
  const o = placed.data.orders?.[0]
  check('trả bằng ví thành công', placed.status === 200 && !!o, JSON.stringify(placed.data).slice(0, 200))
  check('ví bị trừ đúng tổng tiền đơn', num((await ok('GET', '/buyer/wallet', b.token)).balance) === 500000 - num(o.total))
  await ok('PATCH', `/orders/${o.id}/cancel`, b.token, { reason: 'Đổi ý' })
  check('hủy đơn: tiền về lại ví ngay', num((await ok('GET', '/buyer/wallet', b.token)).balance) === 500000)
  const poor = await buyer('poor')
  const r = (await place(poor, [[shirt, 1]], { paymentMethod: 'SHOPPI_PAY' })).placed
  check('ví không đủ tiền thì không đặt được', r.status === 400, r.status)
  const tooMuch = await call('POST', '/buyer/wallet/top-up', b.token, { amount: 5000 })
  check('nạp dưới mức tối thiểu bị từ chối', tooMuch.status === 400, tooMuch.status)
}

// ----------------------------------------------------------------------
section('5. Không bán quá số hàng còn lại (nhiều người mua cùng lúc)')
{
  const buyers = await Promise.all([1, 2, 3, 4, 5, 6].map(i => buyer('race' + i)))
  // everyone has the item in the cart first, then all press "Dat hang" at the same moment
  const carts = []
  for (const b of buyers) carts.push((await ok('POST', '/cart', b.token, { productId: last.id, quantity: 1 })).id)
  const results = await Promise.all(buyers.map((b, k) => call('POST', '/orders/checkout', b.token,
    { shops: [{ cartItemIds: [carts[k]] }], addressId: b.addressId, paymentMethod: 'COD' }).then(r => r.status)))
  check('đúng 3 người mua được 3 sản phẩm cuối', results.filter(s => s === 200).length === 3, results.join(','))
  check('những người còn lại nhận lỗi "hết hàng" (400), không có lỗi hệ thống', results.filter(s => s !== 200).every(s => s === 400), results.join(','))
  check('kho về 0, không âm', (await stockOf(last.id)) === 0)
}

// ----------------------------------------------------------------------
section('6. Giao hàng, đánh giá, trả hàng')
{
  const b = await buyer('review')
  const { placed } = await place(b, [[shirt, 1]])
  const o = placed.data.orders[0]
  const item = (await ok('GET', `/orders/${o.id}`, b.token)).items[0]
  const early = await call('POST', '/buyer/reviews', b.token, { orderItemId: item.id, rating: 5, comment: 'Chưa nhận mà đánh giá' })
  check('chưa nhận hàng thì chưa đánh giá được', early.status === 400, early.status)
  const skip = await call('PATCH', `/seller/orders/${o.id}/status`, shopA.token, { status: 'DELIVERED' })
  check('người bán không nhảy cóc từ "Chờ xác nhận" sang "Đã giao"', skip.status >= 400 && skip.status < 500, skip.status)
  await deliver(shopA, o.id)
  check('đơn đã giao', (await ok('GET', `/orders/${o.id}`, b.token)).status === 'DELIVERED')
  const review = await call('POST', '/buyer/reviews', b.token, { orderItemId: item.id, rating: 4, comment: 'Áo đẹp, giao nhanh, đúng mô tả' })
  check('đánh giá sau khi nhận hàng', review.status === 200, JSON.stringify(review.data).slice(0, 150))
  const twice = await call('POST', '/buyer/reviews', b.token, { orderItemId: item.id, rating: 1, comment: 'Đánh giá lần hai' })
  check('mỗi sản phẩm chỉ đánh giá 1 lần', twice.status === 400, twice.status)
  const stranger = await buyer('stranger')
  const other = await call('POST', '/buyer/reviews', stranger.token, { orderItemId: item.id, rating: 1, comment: 'Không mua mà chê' })
  check('không đánh giá được sản phẩm mình không mua', other.status === 403, other.status)

  const before = await stockOf(shirt.id)
  const rr = await call('POST', `/orders/${o.id}/returns`, b.token, {
    type: 'RETURN_AND_REFUND', reason: 'DAMAGED', description: 'Áo bị rách một đường ở tay áo khi mở hộp', evidenceUrls: ['/uploads/evidence.jpg'],
    items: [{ orderItemId: item.id, quantity: 1 }]
  })
  check('gửi yêu cầu trả hàng', rr.status === 200, JSON.stringify(rr.data).slice(0, 200))
  const wrongShop = await call('POST', `/seller/returns/${rr.data.id}/approve`, shopB.token)
  check('shop khác không duyệt được yêu cầu', wrongShop.status >= 400 && wrongShop.status < 500, wrongShop.status)
  const approved = await call('POST', `/seller/returns/${rr.data.id}/approve`, shopA.token)
  if (approved.status === 200) {
    check('shop duyệt trả hàng', true)
    check('hàng trả về được cộng lại kho', (await stockOf(shirt.id)) === before + 1, `${before} -> ${await stockOf(shirt.id)}`)
  } else {
    // the return-risk AI may hold a risky refund for review: that is a valid outcome, not a failure
    check('shop duyệt hoặc hệ thống tạm giữ để kiểm tra rủi ro', approved.status === 400 && /giữ|kiểm tra|rủi ro/i.test(JSON.stringify(approved.data)), JSON.stringify(approved.data).slice(0, 200))
  }
}

// ----------------------------------------------------------------------
section('7. Phân quyền')
{
  const b = await buyer('perm')
  check('người mua không vào được API người bán', (await call('GET', '/seller/orders', b.token)).status === 403)
  check('người mua không vào được API quản trị', (await call('GET', '/admin/users', b.token)).status === 403)
  check('chưa đăng nhập thì không xem được giỏ hàng', (await call('GET', '/cart')).status === 401)
  const { placed } = await place(b, [[cap, 1]])
  const foreign = await call('GET', `/seller/orders/${placed.data.orders[0].id}`, shopA.token)
  check('shop A không xem được đơn của shop B', foreign.status >= 400 && foreign.status < 500, foreign.status)
  const spy = await buyer('spy')
  const peek = await call('GET', `/orders/${placed.data.orders[0].id}`, spy.token)
  check('người mua không xem được đơn của người khác', peek.status >= 400 && peek.status < 500, peek.status)
}

// ----------------------------------------------------------------------
section('8. Báo khi giảm giá')
{
  const b = await buyer('watch')
  const low = await call('PUT', `/buyer/price-watches/${shirt.id}`, b.token, { targetPrice: 250000 })
  check('giá mong muốn phải thấp hơn giá hiện tại', low.status === 400, low.status)
  const w = await call('PUT', `/buyer/price-watches/${shirt.id}`, b.token, { targetPrice: 150000 })
  check('theo dõi giá với giá mong muốn', w.status === 200 && num(w.data.targetPrice) === 150000, JSON.stringify(w.data).slice(0, 120))
  check('trạng thái theo dõi', (await ok('GET', `/buyer/price-watches/${shirt.id}`, b.token)).watching === true)
  await ok('DELETE', `/buyer/price-watches/${shirt.id}`, b.token)
  check('bỏ theo dõi', (await ok('GET', `/buyer/price-watches/${shirt.id}`, b.token)).watching === false)
}

// ----------------------------------------------------------------------
section('9. Vòng quay may mắn')
{
  const b = await buyer('spin')
  const s0 = await ok('GET', '/buyer/lucky-spin', b.token)
  check('chưa quay hôm nay', s0.spunToday === false && s0.prizes.length === 8)
  // two taps at the same moment: only one turn
  const [r1, r2] = await Promise.all([call('POST', '/buyer/lucky-spin', b.token), call('POST', '/buyer/lucky-spin', b.token)])
  const statuses = [r1.status, r2.status].sort()
  check('bấm 2 lần cùng lúc chỉ được 1 lượt', statuses[0] === 200 && statuses[1] === 400, statuses.join(','))
  const won = r1.status === 200 ? r1.data : r2.data
  check('kết quả trỏ đúng một ô của vòng quay', won.slot >= 0 && won.slot < 8 && s0.prizes[won.slot].code === won.code, JSON.stringify(won))
  check('Xu trúng được cộng vào ví', won.coinBalance === s0.coinBalance + won.coins, `${s0.coinBalance} + ${won.coins} = ${won.coinBalance}`)
  const again = await call('POST', '/buyer/lucky-spin', b.token)
  check('hôm nay không quay thêm được', again.status === 400, again.status)
  const s1 = await ok('GET', '/buyer/lucky-spin', b.token)
  check('trạng thái ghi nhận lượt đã quay', s1.spunToday === true && s1.todayPrize.code === won.code)
  check('người bán không quay được (chỉ người mua)', (await call('POST', '/buyer/lucky-spin', shopA.token)).status === 403)
}

} catch (e) {
  failed++
  console.error('\n✗ Dừng giữa chừng: ' + e.message)
} finally {
  await restoreLimit()
}

console.log(`\n${failed ? '✗' : '✓'} ${passed} đạt, ${failed} lỗi`)
process.exit(failed ? 1 : 0)
