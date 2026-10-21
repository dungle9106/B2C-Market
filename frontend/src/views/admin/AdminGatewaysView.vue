<template>
  <!-- styled with Tailwind utilities (tw- prefix) on top of the shared sc-card blocks -->
  <div>
    <!-- ===== VNPay ===== -->
    <section class="sc-card">
      <div class="sc-card-head tw-items-center">
        <h2 class="sc-card-title">Cổng Thanh Toán VNPay</h2>
        <span class="tw-text-[13px] tw-font-semibold" :class="vn?.ready ? 'tw-text-green-600' : 'tw-text-amber-700'">
          {{ !vn ? '' : vn.ready ? (vn.sandbox ? '● Đang hoạt động (sandbox)' : '● Đang hoạt động') : vn.enabled ? '● Chưa đủ thông tin kết nối' : '● Đang tắt' }}
        </span>
      </div>
      <div v-if="!vn" class="sc-empty">Đang tải...</div>
      <template v-else>
        <div class="tw-grid tw-grid-cols-1 tw-gap-3 md:tw-grid-cols-3">
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500">Mã website (vnp_TmnCode)<input v-model.trim="v.tmnCode" autocomplete="off" placeholder="VD: 2QXUI4J4" /></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500">Chuỗi bí mật (vnp_HashSecret)<input v-model="v.hashSecret" type="password" autocomplete="new-password"
                 :placeholder="vn.secretSet ? '•••••••• (đã lưu, nhập để thay)' : 'Hash secret'" /></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500"><span>Trạng thái</span><span class="tw-flex tw-h-9 tw-items-center tw-gap-1.5 tw-text-neutral-800"><input v-model="v.enabled" type="checkbox" class="!tw-w-auto" /> Cho phép thanh toán qua VNPay</span></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500 md:tw-col-span-3">URL thanh toán<input v-model.trim="v.payUrl" placeholder="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html" /></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500 md:tw-col-span-3">URL API (truy vấn / hoàn tiền)<input v-model.trim="v.apiUrl" placeholder="https://sandbox.vnpayment.vn/merchant_webapi/api/transaction" /></label>
        </div>
        <dl class="tw-my-3.5 tw-grid tw-grid-cols-1 tw-gap-x-3 tw-gap-y-1.5 tw-text-[13px] md:tw-grid-cols-[130px_1fr]">
          <dt class="tw-text-neutral-500">Return URL</dt><dd class="tw-m-0 tw-break-all"><code class="tw-rounded tw-bg-neutral-100 tw-px-1.5 tw-py-px tw-text-xs">{{ vn.returnUrl }}</code></dd>
          <dt class="tw-text-neutral-500">IPN URL</dt><dd class="tw-m-0 tw-break-all"><code class="tw-rounded tw-bg-neutral-100 tw-px-1.5 tw-py-px tw-text-xs">{{ vn.ipnUrl }}</code> — khai báo trong trang quản trị merchant VNPay (cần địa chỉ công khai)</dd>
          <dt class="tw-text-neutral-500">Nguồn cấu hình</dt><dd class="tw-m-0 tw-break-all">{{ SOURCE[vn.source] }}</dd>
        </dl>
        <p class="tw-text-xs tw-text-neutral-400">Đăng ký tài khoản sandbox tại sandbox.vnpayment.vn để nhận TMN code và hash secret. Hash secret được mã hóa khi lưu.</p>
        <div class="tw-flex tw-flex-wrap tw-items-center tw-justify-end tw-gap-2.5">
          <span v-if="vMsg" :class="vOk ? 'tw-text-green-600' : 'tw-text-red-600'" class="tw-text-[13px]">{{ vMsg }}</span>
          <button type="button" class="btn sm" :disabled="busy" @click="saveVn">Lưu cấu hình VNPay</button>
        </div>
      </template>
    </section>

    <!-- ===== GHN ===== -->
    <section class="sc-card">
      <div class="sc-card-head tw-items-center">
        <h2 class="sc-card-title">Đơn Vị Vận Chuyển Giao Hàng Nhanh (GHN)</h2>
        <span class="tw-text-[13px] tw-font-semibold" :class="gh?.ready ? 'tw-text-green-600' : 'tw-text-amber-700'">
          {{ !gh ? '' : gh.ready ? (gh.sandbox ? '● Đã kết nối (sandbox)' : '● Đã kết nối') : gh.enabled ? '● Chưa kết nối: dùng vận chuyển mô phỏng' : '● Đang tắt: dùng vận chuyển mô phỏng' }}
        </span>
      </div>
      <div v-if="!gh" class="sc-empty">Đang tải...</div>
      <template v-else>
        <div class="tw-grid tw-grid-cols-1 tw-gap-3 md:tw-grid-cols-3">
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500">Token API<input v-model="g.token" type="password" autocomplete="new-password"
                 :placeholder="gh.tokenSet ? '•••••••• (đã lưu, nhập để thay)' : 'Token GHN'" /></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500">Mã cửa hàng (ShopId)<input v-model.trim="g.shopId" inputmode="numeric" placeholder="VD: 885" /></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500"><span>Trạng thái</span><span class="tw-flex tw-h-9 tw-items-center tw-gap-1.5 tw-text-neutral-800"><input v-model="g.enabled" type="checkbox" class="!tw-w-auto" /> Dùng GHN thật cho đơn GHN</span></label>
          <label class="tw-flex tw-flex-col tw-gap-1 tw-text-[13px] tw-text-neutral-500 md:tw-col-span-3">URL API<input v-model.trim="g.apiUrl" placeholder="https://dev-online-gateway.ghn.vn/shiip/public-api" /></label>
        </div>
        <dl class="tw-my-3.5 tw-grid tw-grid-cols-1 tw-gap-x-3 tw-gap-y-1.5 tw-text-[13px] md:tw-grid-cols-[130px_1fr]">
          <dt class="tw-text-neutral-500">Webhook</dt><dd class="tw-m-0 tw-break-all"><code class="tw-rounded tw-bg-neutral-100 tw-px-1.5 tw-py-px tw-text-xs">&lt;địa chỉ backend công khai&gt;{{ gh.webhookPath }}</code> — khai báo trong trang khách hàng GHN để nhận trạng thái đơn</dd>
          <dt class="tw-text-neutral-500">Nguồn cấu hình</dt><dd class="tw-m-0 tw-break-all">{{ SOURCE[gh.source] }}</dd>
        </dl>
        <ul class="tw-mb-3 tw-mt-1.5 tw-pl-[18px] tw-text-xs tw-leading-relaxed tw-text-neutral-400">
          <li>Khi đã kết nối: phí ship GHN ở bước thanh toán lấy từ API GHN; đơn GHN được tạo vận đơn thật khi người bán bấm "Chuẩn bị hàng" (mã vận đơn = mã GHN); trạng thái giao hàng cập nhật qua webhook.</li>
          <li>Shop cần khai báo đủ phường/xã và quận/huyện lấy hàng trong Hồ sơ shop. Nếu GHN không báo giá được, hệ thống dùng bảng phí mô phỏng.</li>
          <li>Đăng ký tài khoản thử nghiệm tại 5sao.ghn.dev để lấy token và ShopId. Token được mã hóa khi lưu.</li>
        </ul>
        <div class="tw-flex tw-flex-wrap tw-items-center tw-justify-end tw-gap-2.5">
          <span v-if="gMsg" :class="gOk ? 'tw-text-green-600' : 'tw-text-red-600'" class="tw-text-[13px]">{{ gMsg }}</span>
          <button type="button" class="btn sm ghost" :disabled="busy || !gh.ready" @click="testGh">Kiểm tra kết nối</button>
          <button type="button" class="btn sm" :disabled="busy" @click="saveGh">Lưu cấu hình GHN</button>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { vnpayConfig, saveVnpayConfig, ghnConfig, saveGhnConfig, testGhn } from '../../api/admin'
import { extractError } from '../../utils/apiError'

const SOURCE = { ADMIN: 'Trang quản trị', ENV: 'Biến môi trường', NONE: 'Chưa cấu hình' }

const vn = ref(null)
const gh = ref(null)
const v = ref({})
const g = ref({})
const busy = ref(false)
const vMsg = ref('')
const vOk = ref(true)
const gMsg = ref('')
const gOk = ref(true)

function fillVn(c) {
  vn.value = c
  v.value = { enabled: c.enabled, tmnCode: c.tmnCode || '', hashSecret: '', payUrl: c.payUrl, apiUrl: c.apiUrl }
}
function fillGh(c) {
  gh.value = c
  g.value = { enabled: c.enabled, shopId: c.shopId || '', token: '', apiUrl: c.apiUrl }
}

async function saveVn() {
  busy.value = true
  vMsg.value = ''
  try {
    const body = { ...v.value }
    if (!body.hashSecret) delete body.hashSecret
    fillVn((await saveVnpayConfig(body)).data)
    vOk.value = true
    vMsg.value = 'Đã lưu cấu hình VNPay'
  } catch (e) {
    vOk.value = false
    vMsg.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function saveGh() {
  busy.value = true
  gMsg.value = ''
  try {
    const body = { ...g.value }
    if (!body.token) delete body.token
    fillGh((await saveGhnConfig(body)).data)
    gOk.value = true
    gMsg.value = 'Đã lưu cấu hình GHN'
  } catch (e) {
    gOk.value = false
    gMsg.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function testGh() {
  busy.value = true
  gMsg.value = ''
  try {
    gMsg.value = (await testGhn()).data.message
    gOk.value = true
  } catch (e) {
    gOk.value = false
    gMsg.value = extractError(e)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  const [a, b] = await Promise.allSettled([vnpayConfig(), ghnConfig()])
  if (a.status === 'fulfilled') fillVn(a.value.data)
  if (b.status === 'fulfilled') fillGh(b.value.data)
})
</script>
