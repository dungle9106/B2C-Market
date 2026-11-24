<template>
  <div>
    <AppHeader />
    <div class="container page">
      <section class="stage">
        <h1>Vòng Quay May Mắn</h1>
        <p class="sub">Mỗi ngày 1 lượt quay miễn phí, trúng Shoppi Xu hoặc voucher Freeship</p>

        <div class="wheel-wrap">
          <div class="pointer" aria-hidden="true"></div>
          <svg class="wheel" viewBox="-110 -110 220 220" :style="{ transform: `rotate(${angle}deg)`, transition: spinning ? 'transform 4.6s cubic-bezier(.17,.67,.12,1)' : 'none' }"
               role="img" aria-label="Vòng quay may mắn">
            <circle r="108" fill="#fff3e0" stroke="#f5a623" stroke-width="4" />
            <g v-for="(p, i) in prizes" :key="p.code">
              <path :d="slice(i)" :fill="p.color" stroke="white" stroke-width="1.5" />
              <text :transform="`rotate(${i * step}) translate(0,-68)`" text-anchor="middle" :fill="dark(p.color) ? 'white' : '#7a3b12'"
                    font-size="9" font-weight="700">
                <tspan v-for="(line, k) in lines(p.label)" :key="k" x="0" :dy="k ? 10 : 0">{{ line }}</tspan>
              </text>
            </g>
            <circle v-for="d in 16" :key="'d' + d" :cx="104 * Math.sin(d * Math.PI / 8)" :cy="-104 * Math.cos(d * Math.PI / 8)" r="2.2" fill="#fff" />
          </svg>
          <button type="button" class="hub" :disabled="!canSpin" @click="spin">
            {{ spinning ? '...' : status?.spunToday ? 'Mai quay tiếp' : 'QUAY' }}
          </button>
        </div>

        <p v-if="!auth.isBuyer" class="note">
          <router-link :to="{ name: 'login', query: { redirect: '/account/lucky-spin' } }">Đăng nhập</router-link> bằng tài khoản người mua để quay.
        </p>
        <p v-else-if="error" class="note err">{{ error }}</p>
        <p v-else-if="status?.spunToday && !result" class="note">Hôm nay bạn đã quay: <b>{{ status.todayPrize?.label }}</b>. Quay lại vào ngày mai nhé!</p>
        <p class="balance" v-if="status">Số dư: <b>🪙 {{ Number(status.coinBalance).toLocaleString('vi-VN') }} Xu</b> · <router-link to="/account/coins">Điểm danh nhận thêm Xu ›</router-link></p>
      </section>

      <div class="cols">
        <section class="shopee-section">
          <h2>Giải thưởng</h2>
          <ul class="odds">
            <li v-for="p in sortedPrizes" :key="p.code"><span class="dot" :style="{ background: p.color }"></span>{{ p.label }}<span class="text-muted">{{ p.chance }}%</span></li>
          </ul>
          <p class="text-muted small">Kết quả do hệ thống bốc ngẫu nhiên theo tỷ lệ trên. Giải Xu lớn có giới hạn số lượng mỗi ngày; khi hết, giải được đổi sang mức Xu cao nhất còn lại.</p>
        </section>
        <section class="shopee-section">
          <h2>Người may mắn</h2>
          <ul v-if="status?.winners?.length" class="list">
            <li v-for="(w, i) in status.winners" :key="i"><b>{{ w.name }}</b> trúng <span class="text-primary">{{ w.label }}</span><span class="text-muted">{{ when(w.createdAt) }}</span></li>
          </ul>
          <p v-else class="text-muted small">Chưa có ai trúng giải lớn. Biết đâu là bạn?</p>
          <h2 class="mt">Lượt quay của bạn</h2>
          <ul v-if="status?.history?.length" class="list">
            <li v-for="(h, i) in status.history" :key="i">{{ day(h.date) }}: <b>{{ h.label }}</b></li>
          </ul>
          <p v-else class="text-muted small">Bạn chưa quay lần nào.</p>
        </section>
      </div>
    </div>

    <BaseModal v-if="result" :title="result.coins || result.voucherCode ? 'Chúc mừng bạn!' : 'Tiếc quá!'" :width="360" @close="result = null">
      <div class="win">
        <div class="big">{{ result.coins ? '🪙' : result.voucherCode ? '🎟' : '🍀' }}</div>
        <p v-if="result.coins">Bạn nhận được <b class="text-primary">{{ result.label }}</b>, đã cộng vào ví Xu.</p>
        <p v-else-if="result.voucherCode">Bạn nhận được <b class="text-primary">{{ result.label }}</b>, đã lưu vào Kho Voucher.</p>
        <p v-else>Chúc bạn may mắn lần sau. Ngày mai quay tiếp nhé!</p>
      </div>
      <template #footer>
        <router-link v-if="result.voucherCode" to="/account/vouchers" class="btn secondary">Xem Kho Voucher</router-link>
        <button type="button" class="btn" @click="result = null">OK</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import BaseModal from '../../components/BaseModal.vue'
import { useAuthStore } from '../../stores/auth'
import { luckySpinStatus, luckySpin } from '../../api/coins'
import { extractError } from '../../utils/apiError'

const auth = useAuthStore()
const status = ref(null)
const result = ref(null)
const error = ref('')
const angle = ref(0)
const spinning = ref(false)

// shown before sign-in too, so guests see what they could win
const FALLBACK = [['10 Xu', '#ffd7c9'], ['100 Xu', '#ee4d2d'], ['20 Xu', '#ffe9a8'], ['Freeship 15K', '#26aa99'], ['50 Xu', '#ffd7c9'],
  ['2.000 Xu', '#f5a623'], ['Chúc may mắn lần sau', '#eeeeee'], ['500 Xu', '#ff7337']].map(([label, color], i) => ({ code: 'p' + i, label, color, chance: null }))
const prizes = computed(() => status.value?.prizes || FALLBACK)
const sortedPrizes = computed(() => [...prizes.value].filter(p => p.chance != null).sort((a, b) => b.chance - a.chance))
const step = computed(() => 360 / prizes.value.length)
const canSpin = computed(() => auth.isBuyer && status.value && !status.value.spunToday && !spinning.value)

// slot i is centred on angle i * step, measured clockwise from the top (where the pointer is)
function slice(i) {
  const a0 = (i - 0.5) * step.value * Math.PI / 180
  const a1 = (i + 0.5) * step.value * Math.PI / 180
  const r = 104
  return `M0,0 L${r * Math.sin(a0)},${-r * Math.cos(a0)} A${r},${r} 0 0,1 ${r * Math.sin(a1)},${-r * Math.cos(a1)} Z`
}
function lines(label) {
  if (label.length <= 9) return [label]
  const words = label.split(' ')
  const half = Math.ceil(words.length / 2)
  return [words.slice(0, half).join(' '), words.slice(half).join(' ')]
}
const dark = (hex) => {
  const n = parseInt(hex.slice(1), 16)
  return ((n >> 16) * 299 + ((n >> 8) & 255) * 587 + (n & 255) * 114) / 1000 < 150
}

async function load() {
  if (!auth.isBuyer) return
  try {
    status.value = (await luckySpinStatus()).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function spin() {
  error.value = ''
  spinning.value = true
  try {
    const { data } = await luckySpin()
    // turn so that the won slot ends under the pointer: 6 full turns + the offset, a little off-centre to look natural
    const jitter = (Math.random() - 0.5) * step.value * 0.6
    const target = (360 - data.slot * step.value + jitter) % 360
    const now = ((angle.value % 360) + 360) % 360
    angle.value += 360 * 6 + ((target - now + 360) % 360)
    await new Promise(r => setTimeout(r, 4700))
    result.value = data
    await load()
  } catch (e) {
    error.value = extractError(e)
    await load()
  } finally {
    spinning.value = false
  }
}

const day = (d) => new Date(d).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
const when = (d) => new Date(d).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })

onMounted(load)
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 32px; }
.stage { text-align: center; padding: 22px 12px 18px; border-radius: 6px; color: white;
  background: radial-gradient(circle at 50% 30%, #ff8a3d 0%, #ee4d2d 55%, #c9341a 100%); }
.stage h1 { margin: 0; font-size: 26px; letter-spacing: 0.5px; text-shadow: 0 2px 6px rgba(0, 0, 0, 0.2); }
.sub { margin: 4px 0 16px; opacity: 0.92; font-size: 14px; }
.wheel-wrap { position: relative; width: min(340px, 86vw); aspect-ratio: 1; margin: 0 auto; }
.wheel { width: 100%; height: 100%; filter: drop-shadow(0 8px 16px rgba(0, 0, 0, 0.25)); }
.pointer { position: absolute; top: -6px; left: 50%; transform: translateX(-50%); z-index: 2; width: 0; height: 0;
  border-left: 14px solid transparent; border-right: 14px solid transparent; border-top: 30px solid #fff; filter: drop-shadow(0 2px 2px rgba(0, 0, 0, 0.3)); }
.hub { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 25%; aspect-ratio: 1; border-radius: 50%;
  border: 4px solid #fff; background: linear-gradient(#ffb54d, #f57c00); color: white; font-weight: 800; font-size: 15px; cursor: pointer;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.3); padding: 0; line-height: 1.1; }
.hub:disabled { cursor: default; background: linear-gradient(#ccc, #999); font-size: 11px; }
.hub:not(:disabled):hover { filter: brightness(1.08); }
.note { margin: 14px 0 0; font-size: 14px; }
.note a { color: white; text-decoration: underline; font-weight: 600; }
.note.err { background: rgba(0, 0, 0, 0.25); display: inline-block; padding: 4px 10px; border-radius: 3px; }
.balance { margin: 10px 0 0; font-size: 14px; }
.balance a { color: #fff3c4; }
.cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; }
@media (max-width: 760px) { .cols { grid-template-columns: 1fr; } }
.cols h2 { font-size: 16px; margin: 0 0 10px; }
.cols h2.mt { margin-top: 16px; }
.odds, .list { list-style: none; margin: 0; padding: 0; font-size: 14px; }
.odds li, .list li { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px dashed #eee; }
.odds li .text-muted, .list li .text-muted { margin-left: auto; font-size: 12px; }
.dot { width: 12px; height: 12px; border-radius: 3px; border: 1px solid rgba(0, 0, 0, 0.1); }
.small { font-size: 12px; }
.win { text-align: center; }
.win .big { font-size: 56px; }
</style>
