<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Đăng Sản Phẩm Hàng Loạt</h2>
      <span class="sc-card-sub">Tối đa 200 sản phẩm mỗi lần bằng file Excel (.xlsx)</span>
      <router-link :to="{ name: 'seller-products' }" class="sc-link">‹ Danh sách sản phẩm</router-link>
    </div>

    <ol class="steps">
      <li :class="{ done: true }">
        <span class="num">1</span>
        <div>
          <strong>Tải file mẫu</strong>
          <p>File có sẵn sheet Hướng dẫn, Ví dụ và danh sách Ngành hàng. Các dòng cùng <em>Mã SP</em> là các phân loại của một sản phẩm.</p>
          <button type="button" class="btn sm secondary" :disabled="downloading" @click="onTemplate">{{ downloading ? 'Đang tải...' : '⬇ Tải file mẫu' }}</button>
        </div>
      </li>
      <li :class="{ done: !!result }">
        <span class="num">2</span>
        <div>
          <strong>Tải lên và kiểm tra</strong>
          <p>Shoppi kiểm tra từng dòng trước, chưa đăng sản phẩm nào.</p>
          <label class="drop" :class="{ over: dragging }" @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="onDrop">
            <input type="file" accept=".xlsx" hidden @change="onPick" />
            <span v-if="file">📄 {{ file.name }} <span class="text-faint">({{ (file.size / 1024).toFixed(0) }} KB)</span> · <span class="text-primary">Chọn file khác</span></span>
            <span v-else>Kéo thả file .xlsx vào đây hoặc <span class="text-primary">chọn file</span></span>
          </label>
          <button type="button" class="btn sm" :disabled="!file || checking" @click="check">{{ checking ? 'Đang kiểm tra...' : 'Kiểm tra file' }}</button>
        </div>
      </li>
      <li :class="{ done: created > 0 }">
        <span class="num">3</span>
        <div>
          <strong>Đăng sản phẩm</strong>
          <p>Chỉ đăng khi tất cả các dòng hợp lệ.</p>
        </div>
      </li>
    </ol>

    <p v-if="error" class="banner error">{{ error }}</p>

    <template v-if="result">
      <div v-if="created" class="banner ok">
        ✓ Đã đăng {{ created }} sản phẩm.
        <router-link :to="{ name: 'seller-products' }">Xem danh sách sản phẩm ›</router-link>
      </div>
      <div v-else-if="result.errors.length" class="banner error">
        Tìm thấy {{ result.errors.length }} lỗi trong {{ result.rows }} dòng. Hãy sửa trong file rồi kiểm tra lại.
      </div>
      <div v-else class="banner ok">
        File hợp lệ: {{ result.products.length }} sản phẩm từ {{ result.rows }} dòng.
        <button type="button" class="btn sm" :disabled="importing" @click="doImport">{{ importing ? 'Đang đăng...' : `Đăng ${result.products.length} sản phẩm` }}</button>
      </div>

      <div v-if="result.errors.length" class="sc-table-wrap block">
        <table class="sc-table">
          <thead><tr><th class="row-col">Dòng</th><th>Lỗi</th></tr></thead>
          <tbody>
            <tr v-for="(e, i) in result.errors" :key="i"><td class="row-col">{{ e.row }}</td><td class="err-text">{{ e.message }}</td></tr>
          </tbody>
        </table>
      </div>

      <div v-if="result.products.length && !created" class="sc-table-wrap block">
        <h3 class="sub-title">Xem trước {{ result.errors.length ? 'các sản phẩm hợp lệ' : '' }}</h3>
        <table class="sc-table">
          <thead><tr><th class="row-col">Dòng</th><th>Mã SP</th><th>Tên sản phẩm</th><th>Ngành hàng</th><th>Giá</th><th>Phân loại</th><th>Kho</th></tr></thead>
          <tbody>
            <tr v-for="p in result.products" :key="p.row">
              <td class="row-col">{{ p.row }}</td>
              <td class="mono">{{ p.sku }}</td>
              <td>{{ p.name }}</td>
              <td>{{ p.categoryName || '—' }}</td>
              <td>{{ formatVnd(p.price) }}</td>
              <td>{{ p.variantCount || '—' }}</td>
              <td>{{ p.stock }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { productTemplate, importProducts } from '../../api/products'
import { saveBlob } from '../../utils/download'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const file = ref(null)
const dragging = ref(false)
const downloading = ref(false)
const checking = ref(false)
const importing = ref(false)
const result = ref(null)
const created = ref(0)
const error = ref('')

async function onTemplate() {
  downloading.value = true
  try {
    saveBlob(await productTemplate(), 'mau-dang-san-pham.xlsx')
  } catch {
    error.value = 'Không tải được file mẫu'
  } finally {
    downloading.value = false
  }
}

function setFile(f) {
  error.value = ''
  result.value = null
  created.value = 0
  if (!f) return
  if (!f.name.toLowerCase().endsWith('.xlsx')) {
    error.value = 'Chỉ hỗ trợ file .xlsx'
    return
  }
  file.value = f
}
function onPick(e) {
  setFile(e.target.files[0])
  e.target.value = ''
}
function onDrop(e) {
  dragging.value = false
  setFile(e.dataTransfer.files[0])
}

async function check() {
  checking.value = true
  error.value = ''
  try {
    const { data } = await importProducts(file.value, true)
    result.value = data
  } catch (e) {
    error.value = extractError(e)
    result.value = null
  } finally {
    checking.value = false
  }
}

async function doImport() {
  importing.value = true
  error.value = ''
  try {
    const { data } = await importProducts(file.value, false)
    result.value = data
    created.value = data.created
  } catch (e) {
    error.value = extractError(e)
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.steps { list-style: none; margin: 0 0 18px; padding: 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.steps li { display: flex; gap: 12px; border: 1px solid var(--color-border); border-radius: 6px; padding: 14px; }
.steps li.done { border-color: #ffd9cf; background: #fffbfa; }
.num { width: 26px; height: 26px; border-radius: 50%; background: #eee; color: #777; display: flex; align-items: center; justify-content: center; font-weight: 600; flex-shrink: 0; }
.done .num { background: var(--color-primary); color: white; }
.steps p { font-size: 13px; color: var(--color-text-muted); margin: 4px 0 10px; line-height: 1.5; }
.drop { display: block; border: 1.5px dashed #ccc; border-radius: 4px; padding: 16px; text-align: center; font-size: 13px; cursor: pointer; margin-bottom: 10px; }
.drop.over, .drop:hover { border-color: var(--color-primary); background: var(--color-primary-light); }
.banner { padding: 10px 14px; border-radius: 4px; font-size: 14px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.banner.ok { background: #eaf7ee; color: #1e7e43; }
.banner.ok a { color: #2673dd; }
.banner.error { background: #fdecea; color: #d33; }
.block { margin-top: 14px; }
.sub-title { font-size: 15px; font-weight: 500; margin: 0 0 8px; }
.row-col { width: 64px; text-align: center; }
.err-text { color: #d33; }
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 13px; }
@media (max-width: 900px) { .steps { grid-template-columns: 1fr; } }
</style>
