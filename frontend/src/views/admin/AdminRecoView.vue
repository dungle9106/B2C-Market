<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Mô Hình Gợi Ý Sản Phẩm</h2>
      <span class="sc-card-sub">
        Học từ hành vi mua sắm (xem, thích, thêm giỏ, mua, đánh giá) để chọn "Gợi ý cho bạn" và "Sản phẩm tương tự".
        Tự huấn luyện lại mỗi đêm.
      </span>
    </div>

    <div class="status">
      <span class="dot" :class="state.cls"></span>
      <b>{{ state.text }}</b>
      <span v-if="m.trainedAt" class="faint">· Huấn luyện lúc {{ formatDateTime(m.trainedAt) }}</span>
      <button type="button" class="btn sm" :disabled="training || !m.configured || m.down" @click="onTrain">
        {{ training ? 'Đang huấn luyện...' : m.trained ? 'Huấn luyện lại' : 'Huấn luyện' }}
      </button>
      <span v-if="msg" class="msg">{{ msg }}</span>
    </div>

    <div class="stats">
      <div class="stat"><span>Người mua có hành vi</span><b>{{ formatCount(m.data?.buyers ?? 0) }}</b></div>
      <div class="stat"><span>Sự kiện (xem, thích, giỏ, mua, đánh giá)</span><b>{{ formatCount(m.data?.events ?? 0) }}</b></div>
      <div v-if="m.trained" class="stat"><span>Đã học: người mua × sản phẩm</span><b>{{ formatCount(m.users) }} × {{ formatCount(m.items) }}</b></div>
      <div v-if="m.trained" class="stat"><span>Cặp (người, sản phẩm)</span><b>{{ formatCount(m.interactions) }}</b></div>
    </div>

    <template v-if="metrics">
      <h3 class="h3">Đánh giá trên dữ liệu chưa từng thấy ({{ metrics.testUsers }} người mua)</h3>
      <p class="faint small">
        Với mỗi người mua, giấu đi sản phẩm cuối cùng họ quan tâm, rồi xem mỗi cách có đưa được sản phẩm đó vào top 10 gợi ý hay không.
      </p>
      <table class="tbl">
        <thead>
          <tr><th>Cách gợi ý</th><th>HitRate@10</th><th>NDCG@10</th><th>Độ phủ danh mục</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.key" :class="{ best: row.key === 'model' }">
            <td>{{ row.label }}</td>
            <td v-for="k in ['hitRate', 'ndcg', 'coverage']" :key="k">
              <div class="bar"><i :style="{ width: (100 * row.v[k] / maxOf(k)) + '%' }"></i></div>
              <span>{{ pct(row.v[k]) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="gain !== null" class="gain">
        {{ gain >= 0 ? `Mô hình xếp hạng tốt hơn luật tốt nhất ${gain}% (NDCG@10).` : `Mô hình chưa tốt hơn luật (${gain}%): cần thêm dữ liệu hành vi.` }}
      </p>
      <p class="faint small">
        Tham số chọn trên tập kiểm định: β (cùng danh mục) = {{ m.params?.beta }}, γ (độ phổ biến) = {{ m.params?.gamma }}.
        HitRate@10: tỉ lệ người mua có sản phẩm đúng trong top 10. NDCG@10: như HitRate nhưng thưởng thêm khi xếp càng cao.
        Độ phủ: tỉ lệ sản phẩm của sàn từng được gợi ý cho ít nhất một người.
      </p>
    </template>
    <p v-else-if="m.trained" class="faint small">Chưa đủ người mua có từ 3 sản phẩm trở lên để chấm điểm mô hình.</p>

    <details class="how">
      <summary>Mô hình hoạt động thế nào?</summary>
      <ol>
        <li>Mỗi hành vi có trọng số: xem 1, thích 2, thêm giỏ 3, mua 5, đánh giá 4-5 sao +2, đánh giá 1-2 sao −3.</li>
        <li>Lọc cộng tác theo sản phẩm (item-based collaborative filtering): hai sản phẩm giống nhau khi được cùng những người quan tâm
          (độ tương đồng cosine, giữ 50 sản phẩm gần nhất của mỗi sản phẩm).</li>
        <li>Điểm của sản phẩm cho một người = tổng độ tương đồng với những gì họ đã quan tâm, cộng thêm một phần "cùng danh mục" và "độ phổ biến"
          để sản phẩm mới vẫn có cơ hội.</li>
        <li>Khi mô hình chưa huấn luyện hoặc dịch vụ AI tắt, sàn tự quay về luật cũ (cùng danh mục, bán chạy).</li>
      </ol>
    </details>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { recoStatus, trainReco } from '../../api/admin'
import { extractError } from '../../utils/apiError'
import { formatCount } from '../../utils/format'

const m = ref({})
const training = ref(false)
const msg = ref('')

const state = computed(() => {
  if (!m.value.configured) return { cls: 'off', text: 'Chưa cấu hình dịch vụ AI (REVIEW_AI_URL): đang dùng luật cũ' }
  if (m.value.down) return { cls: 'off', text: 'Không kết nối được dịch vụ AI: đang dùng luật cũ' }
  if (!m.value.trained) return { cls: 'wait', text: `Chưa huấn luyện (cần ít nhất ${m.value.minUsers} người mua và ${m.value.minInteractions} lượt tương tác)` }
  return { cls: 'on', text: 'Đang dùng mô hình đã huấn luyện' }
})

const metrics = computed(() => (m.value.metrics && m.value.metrics.model ? m.value.metrics : null))
const rows = computed(() => metrics.value ? [
  { key: 'popular', label: 'Bán chạy nhất (luật cũ)', v: metrics.value.popular },
  { key: 'sameCategory', label: 'Cùng danh mục đã xem (luật cũ)', v: metrics.value.sameCategory },
  { key: 'model', label: 'Mô hình học từ hành vi', v: metrics.value.model }
] : [])
const maxOf = (k) => Math.max(0.0001, ...rows.value.map(r => r.v[k]))
const pct = (x) => `${(100 * x).toFixed(1)}%`
const gain = computed(() => {
  if (!metrics.value) return null
  const rule = Math.max(metrics.value.popular.ndcg, metrics.value.sameCategory.ndcg)
  return rule > 0 ? Math.round(100 * (metrics.value.model.ndcg / rule - 1)) : null
})

function formatDateTime(iso) {
  return new Date(iso).toLocaleString('vi-VN')
}

const load = () => recoStatus().then(({ data }) => { m.value = data }).catch(() => {})

async function onTrain() {
  training.value = true
  msg.value = ''
  try {
    const { data } = await trainReco()
    m.value = { ...m.value, ...data }
    msg.value = 'Đã huấn luyện xong'
    load()
  } catch (e) {
    msg.value = extractError(e)
  } finally {
    training.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.status { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 14px; margin-bottom: 14px; }
.dot { width: 10px; height: 10px; border-radius: 50%; background: #bbb; }
.dot.on { background: #26aa99; } .dot.wait { background: #d98a00; } .dot.off { background: #d0011b; }
.faint { color: var(--color-text-faint); }
.small { font-size: 12px; line-height: 1.6; }
.msg { color: var(--color-text-muted); font-size: 13px; }
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin-bottom: 18px; }
.stat { background: #fafafa; border: 1px solid var(--color-border); border-radius: 4px; padding: 10px 12px; display: flex; flex-direction: column; gap: 4px; }
.stat span { font-size: 12px; color: var(--color-text-muted); }
.stat b { font-size: 18px; }
.h3 { font-size: 15px; margin: 8px 0 4px; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; margin: 8px 0; }
.tbl th, .tbl td { text-align: left; padding: 8px; border-bottom: 1px solid var(--color-border); }
.tbl th { color: var(--color-text-muted); font-weight: 500; }
.tbl td span { font-variant-numeric: tabular-nums; }
.tbl tr.best td:first-child { font-weight: 600; color: var(--color-primary); }
.bar { height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; margin-bottom: 3px; max-width: 160px; }
.bar i { display: block; height: 100%; background: #c5c5c5; }
.tbl tr.best .bar i { background: var(--color-primary); }
.gain { font-weight: 600; font-size: 14px; margin: 6px 0; }
.how { margin-top: 14px; font-size: 13px; }
.how summary { cursor: pointer; color: #2673dd; }
.how ol { padding-left: 18px; line-height: 1.7; color: var(--color-text-muted); }
</style>
