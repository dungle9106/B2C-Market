<template>
  <section class="risk">
    <div v-if="!d" class="faint">{{ error || 'Đang tải phân tích AI...' }}</div>
    <template v-else>
      <header class="head">
        <strong>AI phát hiện lạm dụng đổi trả</strong>
        <span class="faint">Chỉ admin thấy. AI chỉ gợi ý, không tự từ chối yêu cầu.</span>
        <button type="button" class="btn sm ghost" :disabled="busy" @click="reanalyze">{{ busy ? 'Đang phân tích...' : 'Phân tích lại' }}</button>
      </header>

      <p v-if="!a" class="faint">Yêu cầu này chưa được phân tích, hệ thống sẽ xử lý trong ít phút. Bạn có thể bấm "Phân tích lại".</p>
      <template v-else>
        <div class="score" :class="a.level">
          <b>{{ a.score }}</b><span>/100</span>
          <em>{{ RISK_LEVEL[a.level] }}</em>
          <span class="faint engine">{{ engine }} · {{ formatDateTime(a.updatedAt) }}</span>
        </div>

        <div v-if="a.holdUntil" class="hold">
          <span>⏸ <b>Đang tạm giữ hoàn tiền</b>: shop chưa thể đồng ý hoàn tiền đến {{ formatDateTime(a.holdUntil) }}. Shop vẫn có thể từ chối.</span>
          <button type="button" class="btn sm" :disabled="busy" @click="release">Cho phép shop xử lý</button>
        </div>
        <p v-else-if="a.holdReleasedAt" class="faint">Đã bỏ tạm giữ lúc {{ formatDateTime(a.holdReleasedAt) }}.</p>

        <p v-if="!a.signals.length" class="faint">Không có tín hiệu đáng chú ý.</p>
        <ul class="signals">
          <li v-for="s in a.signals" :key="s.code">
            <span class="pts" :class="{ good: s.points < 0 }">{{ s.points > 0 ? '+' : '' }}{{ s.points }}</span>
            <span><b>{{ s.label }}</b><small>{{ s.detail }}</small></span>
          </li>
        </ul>

        <div v-if="a.aiReview" class="ai">
          <strong>Nhận xét của AI <span class="faint">(tham khảo, không ảnh hưởng điểm)</span></strong>
          <p>{{ a.aiReview.summary }}</p>
          <div v-for="b in AI_BLOCKS" :key="b.key">
            <template v-if="a.aiReview[b.key]?.length">
              <b>{{ b.label }}</b>
              <ul><li v-for="x in a.aiReview[b.key]" :key="x">{{ x }}</li></ul>
            </template>
          </div>
          <p v-if="a.aiReview.image" class="image-note">
            <b>Ảnh bằng chứng:</b> {{ a.aiReview.image.note }}
            <span v-if="a.aiReview.image.screenshot" class="flag">Giống ảnh chụp màn hình / ảnh mẫu</span>
          </p>
        </div>

        <div v-if="d.history.length" class="history">
          <strong>Lịch sử trả hàng của người mua ({{ d.history.length }})</strong>
          <table>
            <tbody>
              <tr v-for="h in d.history" :key="h.id">
                <td>{{ formatDateTime(h.createdAt) }}</td>
                <td>{{ h.orderCode }}<div class="faint">{{ h.shopName }}</div></td>
                <td>{{ RETURN_REASONS[h.reason] }}</td>
                <td class="r">{{ formatVnd(h.refundAmount) }}</td>
                <td>{{ RETURN_STATUS[h.status] }}<span v-if="h.label === 'ABUSE'" class="flag">Lạm dụng</span></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="verdict">
          <strong>Kết luận của bạn</strong>
          <span class="faint">Dùng để huấn luyện mô hình, không tự động xử lý tài khoản. Kết luận "Hợp lệ" sẽ bỏ tạm giữ hoàn tiền.</span>
          <input v-model.trim="note" maxlength="500" placeholder="Ghi chú (không bắt buộc)" />
          <div class="actions">
            <button type="button" class="btn sm" :class="{ ghost: a.label !== 'ABUSE' }" :disabled="busy" @click="label('ABUSE')">Xác nhận lạm dụng</button>
            <button type="button" class="btn sm" :class="{ ghost: a.label !== 'LEGIT' }" :disabled="busy" @click="label('LEGIT')">Hợp lệ</button>
            <button v-if="a.label" type="button" class="link" :disabled="busy" @click="label(null)">Xóa kết luận</button>
          </div>
        </div>
      </template>
      <p v-if="error" class="error">{{ error }}</p>
    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { returnRiskDetail, reanalyzeReturnRisk, reviewReturnRisk, releaseReturnHold } from '../../api/admin'
import { extractError } from '../../utils/apiError'
import { formatVnd, formatDateTime } from '../../utils/format'
import { RETURN_STATUS, RETURN_REASONS, RISK_LEVEL } from '../../utils/returns'

const props = defineProps({ returnId: { type: Number, required: true } })
const emit = defineEmits(['changed'])

const AI_BLOCKS = [{ key: 'concerns', label: 'Điểm cần lưu ý' }, { key: 'mitigating', label: 'Điểm có lợi cho người mua' }, { key: 'verify', label: 'Nên kiểm tra thêm' }]

const d = ref(null)
const note = ref('')
const busy = ref(false)
const error = ref('')
const a = computed(() => d.value?.assessment)
const engine = computed(() => (a.value.engine || '').replace('RULES', 'Luật').replace('MODEL', 'Mô hình').replace('LLM', 'LLM'))

function show(data) {
  d.value = data
  note.value = data.assessment?.labelNote || ''
}

async function load() {
  d.value = null
  error.value = ''
  try {
    show((await returnRiskDetail(props.returnId)).data)
  } catch (e) {
    error.value = extractError(e)
  }
}

async function run(call) {
  busy.value = true
  error.value = ''
  try {
    show((await call()).data)
    emit('changed')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}
const reanalyze = () => run(() => reanalyzeReturnRisk(props.returnId))
const label = (value) => run(() => reviewReturnRisk(props.returnId, value, note.value))
const release = () => run(() => releaseReturnHold(props.returnId))

watch(() => props.returnId, load, { immediate: true })
</script>

<style scoped>
.risk { border: 1px solid rgba(0, 0, 0, 0.09); border-radius: 4px; padding: 14px; margin: 12px 0; font-size: 14px; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.head .btn { margin-left: auto; }
.score { display: flex; align-items: baseline; gap: 4px; margin: 10px 0; flex-wrap: wrap; }
.score b { font-size: 32px; font-weight: 600; }
.score em { font-style: normal; font-size: 12px; font-weight: 600; color: white; padding: 1px 8px; border-radius: 10px; margin-left: 8px; }
.score.LOW b { color: #26aa99; } .score.LOW em { background: #26aa99; }
.score.MEDIUM b { color: #d98a00; } .score.MEDIUM em { background: #d98a00; }
.score.HIGH b { color: #d0011b; } .score.HIGH em { background: #d0011b; }
.engine { margin-left: auto; }
.hold { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; background: #fff4e0; color: #8a5a00; border-radius: 4px; padding: 8px 12px; margin-bottom: 10px; }
.hold .btn { margin-left: auto; }
.signals { list-style: none; padding: 0; margin: 0 0 10px; }
.signals li { display: flex; gap: 10px; padding: 6px 0; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); }
.signals small { display: block; color: var(--color-text-muted); }
.pts { min-width: 36px; text-align: right; font-weight: 600; color: #d0011b; }
.pts.good { color: #26aa99; }
.ai { background: #f6f8ff; border-radius: 4px; padding: 10px 12px; margin-bottom: 10px; }
.ai p { margin: 6px 0; }
.ai ul { margin: 2px 0 8px; padding-left: 18px; }
.flag { color: #d0011b; font-size: 12px; margin-left: 6px; }
.history table { width: 100%; border-collapse: collapse; font-size: 13px; margin: 6px 0 10px; }
.history td { padding: 5px 6px 5px 0; border-bottom: 1px dashed rgba(0, 0, 0, 0.09); vertical-align: top; }
.r { text-align: right; white-space: nowrap; }
.verdict { display: flex; flex-direction: column; gap: 6px; }
.verdict input { margin: 0; }
.actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
</style>
