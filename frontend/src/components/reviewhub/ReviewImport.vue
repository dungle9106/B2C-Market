<template>
  <!-- bring reviews from other marketplaces: the review export of their seller centre, as it is -->
  <section class="sc-card">
    <div class="sc-card-head"><h2 class="sc-card-title">Gom Đánh Giá Từ Các Sàn</h2></div>
    <div class="tw-grid tw-gap-4 md:tw-grid-cols-2">
      <div>
        <ol class="tw-m-0 tw-pl-5 tw-text-sm tw-leading-7">
          <li>Vào trang người bán của sàn (Shopee: <em>Chăm sóc khách hàng → Quản lý đánh giá</em>; Lazada: <em>Đánh giá sản phẩm</em>; Tiki, TikTok Shop tương tự) và <strong>xuất file Excel/CSV</strong>.</li>
          <li>Chọn sàn và tải file lên. Hệ thống tự nhận các cột <em>Số sao, Nội dung, Tên sản phẩm, Người mua, Thời gian, Mã đơn</em>.</li>
          <li>Đánh giá trùng (đã nhập trước đó) được bỏ qua, nên có thể nhập lại file mới mỗi tuần.</li>
        </ol>
        <p class="tw-text-sm">Đánh giá trên <strong>Shoppi</strong> được đồng bộ tự động.
          <button type="button" class="link" @click="downloadTemplate">Tải file mẫu</button></p>
      </div>
      <form class="tw-flex tw-flex-col tw-gap-2" @submit.prevent="upload">
        <label class="tw-text-sm">Sàn
          <select v-model="source"><option v-for="(l, k) in OTHER" :key="k" :value="k">{{ l }}</option></select>
        </label>
        <label class="tw-text-sm">File (.xlsx, .csv)
          <input ref="fileEl" type="file" accept=".xlsx,.xls,.csv" @change="file = $event.target.files[0]" />
        </label>
        <button type="submit" class="btn" :disabled="!file || busy">{{ busy ? 'Đang nhập và phân tích...' : 'Nhập đánh giá' }}</button>
        <p v-if="error" class="tw-m-0 tw-text-sm tw-text-red-600">{{ error }}</p>
      </form>
    </div>

    <div v-if="result" class="tw-mt-4 tw-rounded tw-bg-green-50 tw-p-3 tw-text-sm">
      <strong>Đã nhập {{ result.added }} đánh giá {{ SOURCES[result.source] }}</strong> từ {{ result.rows }} dòng
      · {{ result.duplicates }} đã có từ trước · {{ result.matchedProducts }} khớp với sản phẩm của shop.
      <ul v-if="result.skipped.length" class="tw-mb-0 tw-mt-1 tw-text-red-700">
        <li v-for="s in result.skipped" :key="s.line">Dòng {{ s.line }}: {{ s.reason }}</li>
      </ul>
      <div class="tw-mt-1 tw-text-neutral-600">Hệ thống đã phân loại, chấm điểm ưu tiên và kiểm tra bất thường. Xem trong <button type="button" class="link" @click="$emit('done')">Hộp đánh giá</button>.</div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { hubImport, hubTemplate, SOURCES } from '../../api/reviewHub'
import { extractError } from '../../utils/apiError'

const emit = defineEmits(['done', 'changed'])
const OTHER = Object.fromEntries(Object.entries(SOURCES).filter(([k]) => k !== 'SHOPPI'))
const source = ref('SHOPEE')
const file = ref(null)
const fileEl = ref(null)
const busy = ref(false)
const error = ref('')
const result = ref(null)

async function upload() {
  busy.value = true
  error.value = ''
  result.value = null
  try {
    result.value = (await hubImport(file.value, source.value)).data
    file.value = null
    if (fileEl.value) fileEl.value.value = ''
    emit('changed')
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function downloadTemplate() {
  const { data } = await hubTemplate()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(data)
  a.download = 'mau-nhap-danh-gia.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
</script>

<style scoped>
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 14px; padding: 0; }
</style>
