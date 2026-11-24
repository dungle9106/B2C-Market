<template>
  <div>
    <AppHeader />
    <div class="container page">
      <section class="shopee-section head">
        <h1 class="tw-m-0 tw-text-lg">Kho Voucher</h1>
        <span class="tw-text-sm tw-text-neutral-500">{{ data ? data.usable + ' voucher dùng được' : '' }}</span>
        <form class="code" @submit.prevent="saveCode">
          <input v-model.trim="code" placeholder="Nhập mã voucher shop gửi bạn" maxlength="20" />
          <button type="submit" class="btn sm" :disabled="!code || busy">Lưu</button>
        </form>
      </section>
      <p v-if="msg" class="tw-text-sm" :class="ok ? 'tw-text-green-700' : 'tw-text-red-600'">{{ msg }}</p>

      <nav class="tabs">
        <button v-for="t in TABS" :key="t.key" type="button" :class="{ active: tab === t.key }" @click="tab = t.key">{{ t.label }}</button>
      </nav>

      <div v-if="!data" class="shopee-section empty">Đang tải...</div>
      <div v-else-if="!shown.length" class="shopee-section empty">Chưa có voucher nào ở mục này. Bấm "Lưu" voucher ở trang shop, trang sản phẩm hoặc mục gợi ý bên dưới.</div>
      <div v-else class="grid">
        <article v-for="it in shown" :key="it.voucher.id" class="ticket" :class="{ off: it.state !== 'USABLE' }">
          <div class="stub" :class="it.voucher.scope === 'PLATFORM' ? 'platform' : 'shop'">
            <span class="ico">{{ it.voucher.type === 'SHIPPING' ? '🚚' : '%' }}</span>
            <span class="who">{{ it.voucher.scope === 'PLATFORM' ? 'SHOPPI' : it.voucher.shopName }}</span>
          </div>
          <div class="body">
            <strong>{{ it.voucher.title }}</strong>
            <span class="cond">Đơn tối thiểu {{ formatVnd(it.voucher.minOrder || 0) }}<template v-if="it.voucher.audience && it.voucher.audience !== 'ALL'"> · {{ it.voucher.audienceLabel }}</template></span>
            <span class="cond">HSD {{ formatDateTime(it.voucher.endAt) }} · <span class="code-chip">{{ it.voucher.code }}</span></span>
            <span v-if="it.state !== 'USABLE'" class="why">{{ STATE[it.state] }}{{ it.reason && it.state === 'UNAVAILABLE' ? ': ' + it.reason : '' }}</span>
          </div>
          <div class="side">
            <router-link v-if="it.state === 'USABLE'" :to="useLink(it.voucher)" class="btn sm">Dùng ngay</router-link>
            <button type="button" class="link" @click="remove(it)">Xóa</button>
          </div>
        </article>
      </div>

      <section v-if="data && data.collectable.length" class="shopee-section collect">
        <h2 class="tw-mt-0 tw-text-base">Voucher có thể lưu <span class="tw-text-sm tw-font-normal tw-text-neutral-500">— Shoppi Voucher và voucher của shop bạn theo dõi</span></h2>
        <div class="grid">
          <article v-for="v in data.collectable" :key="v.id" class="ticket">
            <div class="stub" :class="v.scope === 'PLATFORM' ? 'platform' : 'shop'">
              <span class="ico">{{ v.type === 'SHIPPING' ? '🚚' : '%' }}</span>
              <span class="who">{{ v.scope === 'PLATFORM' ? 'SHOPPI' : v.shopName }}</span>
            </div>
            <div class="body">
              <strong>{{ v.title }}</strong>
              <span class="cond">Đơn tối thiểu {{ formatVnd(v.minOrder || 0) }} · HSD {{ formatDateTime(v.endAt) }}</span>
            </div>
            <div class="side"><button type="button" class="btn sm" @click="save(v)">Lưu</button></div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import { voucherWallet, saveVoucher, saveVoucherCode, unsaveVoucher } from '../../api/vouchers'
import { formatVnd, formatDateTime } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const TABS = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PLATFORM', label: 'Shoppi Voucher' },
  { key: 'SHOP', label: 'Voucher của Shop' },
  { key: 'EXPIRED', label: 'Hết hiệu lực' }
]
const STATE = { UPCOMING: 'Chưa đến thời gian sử dụng', UNAVAILABLE: 'Chưa dùng được', EXPIRED: 'Đã hết hạn' }

const data = ref(null)
const tab = ref('ALL')
const code = ref('')
const busy = ref(false)
const msg = ref('')
const ok = ref(true)

const shown = computed(() => (data.value?.items || []).filter(it => {
  if (tab.value === 'EXPIRED') return it.state === 'EXPIRED'
  if (it.state === 'EXPIRED') return false
  return tab.value === 'ALL' || it.voucher.scope === tab.value
}))
// a shop voucher takes you to the shop, a Shoppi one to the products
const useLink = (v) => (v.scope === 'SHOP' ? { name: 'shop', params: { id: v.shopId } } : { name: 'search', query: { q: '' } })

async function load() {
  data.value = (await voucherWallet()).data
}

async function run(fn, success) {
  busy.value = true
  msg.value = ''
  try {
    await fn()
    ok.value = true
    msg.value = success
    await load()
  } catch (e) {
    ok.value = false
    msg.value = extractError(e)
  } finally {
    busy.value = false
  }
}

const saveCode = () => run(async () => { await saveVoucherCode(code.value); code.value = '' }, 'Đã lưu voucher vào kho')
const save = (v) => run(() => saveVoucher(v.id), `Đã lưu ${v.code}`)
const remove = (it) => run(() => unsaveVoucher(it.voucher.id), `Đã xóa ${it.voucher.code} khỏi kho`)

onMounted(load)
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.head { display: flex; align-items: center; gap: 14px; padding: 16px 20px; flex-wrap: wrap; }
.code { margin-left: auto; display: flex; gap: 8px; }
.code input { margin: 0; width: 240px; }
.tabs { display: flex; background: white; box-shadow: var(--shadow-card); margin: 12px 0; }
.tabs button { flex: 1; background: none; border: none; border-bottom: 2px solid transparent; padding: 14px; cursor: pointer; font-size: 14px; }
.tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.empty { text-align: center; padding: 40px 16px; color: var(--color-text-muted); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 10px; }
.ticket { display: flex; background: white; box-shadow: var(--shadow-card); border-radius: 3px; min-height: 104px; }
.ticket.off { opacity: .6; }
.stub { width: 104px; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; color: white;
  text-align: center; padding: 8px; border-right: 2px dashed white; }
.stub.platform { background: var(--color-primary); }
.stub.shop { background: #26aa99; }
.ico { font-size: 22px; }
.who { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 90px; }
.body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 3px; padding: 10px 12px; min-width: 0; }
.cond { font-size: 12px; color: var(--color-text-muted); }
.why { font-size: 12px; color: #b26a00; }
.code-chip { border: 1px dashed var(--color-border); padding: 0 4px; }
.side { display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 6px; padding: 0 12px; }
.link { background: none; border: none; color: var(--color-text-faint); cursor: pointer; font-size: 12px; }
.collect { margin-top: 16px; padding: 16px 20px; }
</style>
