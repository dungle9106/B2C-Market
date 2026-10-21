<template>
  <div>
    <div v-if="!settings" class="sc-card sc-empty">{{ error || 'Đang tải...' }}</div>
    <template v-else>
      <!-- fees -->
      <section class="sc-card">
        <div class="sc-card-head">
          <h2 class="sc-card-title">Phí Sàn & Đối Soát</h2>
          <span class="sc-card-sub">Áp dụng cho các bảng đối soát tạo sau thời điểm lưu; bảng đã tạo giữ nguyên mức phí cũ</span>
        </div>
        <div class="row-setting">
          <div>
            <strong>Phí cố định mặc định</strong>
            <p>% trên giá trị hàng (sau khuyến mãi của shop) của mỗi đơn thành công, cho các ngành hàng không có mức phí riêng.</p>
          </div>
          <div class="num"><input v-model.number="fees.commissionPercent" type="number" min="0" max="30" step="0.5" /> %</div>
        </div>
        <div class="cat-fees">
          <strong>Phí theo ngành hàng</strong>
          <p>Để trống = dùng mức mặc định. Ngành hàng con dùng mức phí của ngành hàng cấp 1.</p>
          <div class="cat-grid">
            <label v-for="c in roots" :key="c.id" class="cat" :class="{ custom: fees.categoryCommission[c.id] !== '' && fees.categoryCommission[c.id] != null }">
              <span>{{ c.name }}</span>
              <span class="num"><input v-model="fees.categoryCommission[c.id]" type="number" min="0" max="30" step="0.5" :placeholder="String(fees.commissionPercent)" /> %</span>
            </label>
          </div>
        </div>
        <div class="row-setting">
          <div>
            <strong>Thời gian giữ tiền trước đối soát</strong>
            <p>Số ngày sau khi giao thành công trước khi đơn được đối soát cho người bán (thời gian người mua có thể yêu cầu trả hàng).</p>
          </div>
          <div class="num"><input v-model.number="fees.settlementHoldDays" type="number" min="1" max="30" /> ngày</div>
        </div>
        <div class="actions">
          <span v-if="msg.fees" class="ok">{{ msg.fees }}</span>
          <button type="button" class="btn sm" :disabled="saving" @click="saveFees">Lưu phí & đối soát</button>
        </div>
      </section>

      <!-- operations -->
      <section class="sc-card">
        <div class="sc-card-head"><h2 class="sc-card-title">Vận Hành Đơn Hàng</h2></div>
        <div class="row-setting">
          <div>
            <strong>Thời hạn thanh toán online</strong>
            <p>Đơn thanh toán bằng ví/thẻ/chuyển khoản tự hủy (và hoàn lại tồn kho) nếu người mua không thanh toán trong thời gian này.</p>
          </div>
          <div class="num"><input v-model.number="ops.paymentWindowMinutes" type="number" min="5" max="120" /> phút</div>
        </div>
        <div class="row-setting">
          <div>
            <strong>Hạn người bán giải trình tranh chấp</strong>
            <p>Khi người mua khiếu nại lên Shoppi, người bán cần gửi giải trình và bằng chứng trong thời gian này.</p>
          </div>
          <div class="num"><input v-model.number="ops.disputeResponseHours" type="number" min="12" max="168" /> giờ</div>
        </div>
        <div class="actions">
          <span v-if="msg.ops" class="ok">{{ msg.ops }}</span>
          <button type="button" class="btn sm" :disabled="saving" @click="saveOps">Lưu</button>
        </div>
      </section>

      <!-- sign-in security -->
      <section class="sc-card">
        <div class="sc-card-head"><h2 class="sc-card-title">Bảo Mật Đăng Nhập</h2></div>
        <div class="row-setting">
          <div><strong>Khóa tạm khi nhập sai mật khẩu</strong><p>Sau số lần nhập sai liên tiếp, tài khoản bị khóa đăng nhập trong một khoảng thời gian (chủ tài khoản được báo qua email).</p></div>
          <div class="num"><input v-model.number="sec.maxFailedLogins" type="number" min="3" max="20" /> lần →
            <input v-model.number="sec.lockMinutes" type="number" min="1" max="1440" /> phút</div>
        </div>
        <div class="row-setting">
          <div><strong>Chặn địa chỉ IP dò mật khẩu</strong><p>Số lần đăng nhập sai tối đa của một địa chỉ IP trong 15 phút.</p></div>
          <div class="num"><input v-model.number="sec.ipFailLimit" type="number" min="1" max="1000" /> lần</div>
        </div>
        <div class="row-setting">
          <div><strong>Giới hạn tạo tài khoản</strong><p>Số tài khoản mới tối đa từ một địa chỉ IP trong 10 phút (chống tạo tài khoản ảo hàng loạt).</p></div>
          <div class="num"><input v-model.number="sec.registerPerIp" type="number" min="1" max="1000" /> tài khoản</div>
        </div>
        <div class="setting">
          <div><strong>Bắt buộc xác thực 2 lớp cho quản trị viên</strong>
            <p>Quản trị viên chưa bật xác thực 2 lớp chỉ vào được trang Bảo mật tài khoản cho tới khi bật. Hãy bật cho chính bạn trước.</p></div>
          <button type="button" class="switch" :class="{ on: sec.requireAdmin2fa }" @click="sec.requireAdmin2fa = !sec.requireAdmin2fa"><span></span></button>
        </div>
        <div class="actions">
          <span v-if="msg.sec" class="ok">{{ msg.sec }}</span>
          <button type="button" class="btn sm" :disabled="saving" @click="save({ ...sec }, 'sec')">Lưu</button>
        </div>
      </section>

      <!-- moderation -->
      <section class="sc-card">
        <div class="sc-card-head"><h2 class="sc-card-title">Kiểm Duyệt</h2></div>
        <div class="setting">
          <div>
            <strong>Duyệt shop mới trước khi hiển thị</strong>
            <p>Shop mới đăng ký ở trạng thái "Chờ duyệt": người bán vẫn thiết lập shop và đăng sản phẩm, nhưng người mua chỉ thấy shop
              sau khi được nhân viên có quyền "Duyệt & quản lý shop" phê duyệt. Shop đã hoạt động không bị ảnh hưởng.</p>
          </div>
          <button type="button" class="switch" :class="{ on: settings.requireShopApproval }" :disabled="saving" @click="save({ requireShopApproval: !settings.requireShopApproval })"><span></span></button>
        </div>
        <div class="setting">
          <div>
            <strong>Duyệt sản phẩm mới trước khi hiển thị</strong>
            <p>Bật: mọi sản phẩm mới chờ nhân viên có quyền "Sản phẩm & danh mục" duyệt. Tắt: sản phẩm hiển thị ngay; hệ thống vẫn
              tự kiểm tra nội dung và giữ lại sản phẩm chứa từ khóa cấm.</p>
          </div>
          <button type="button" class="switch" :class="{ on: settings.requireProductApproval }" :disabled="saving" @click="save({ requireProductApproval: !settings.requireProductApproval })"><span></span></button>
        </div>
        <div class="keywords">
          <strong>Từ khóa cấm trong sản phẩm</strong>
          <p>Sản phẩm có tên hoặc mô tả chứa một trong các từ/cụm từ này (so khớp nguyên từ, không phân biệt dấu) sẽ tự động bị giữ lại
            chờ duyệt. Mỗi dòng một từ khóa.</p>
          <textarea v-model="keywordText" rows="8"></textarea>
          <div class="kw-actions">
            <span class="faint">{{ keywordList.length }} từ khóa</span>
            <button type="button" class="btn sm" :disabled="saving || !keywordsChanged" @click="save({ bannedKeywords: keywordList })">Lưu từ khóa</button>
          </div>
        </div>
      </section>
      <p v-if="error" class="error">{{ error }}</p>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminSettings, saveAdminSettings } from '../../api/admin'
import { categoryTree } from '../../api/products'
import { extractError } from '../../utils/apiError'

const settings = ref(null)
const roots = ref([])
const fees = ref({ commissionPercent: 5, categoryCommission: {}, settlementHoldDays: 3 })
const ops = ref({ paymentWindowMinutes: 15, disputeResponseHours: 48 })
const sec = ref({})
const keywordText = ref('')
const saving = ref(false)
const msg = ref({})
const error = ref('')

const keywordList = computed(() => [...new Set(keywordText.value.split('\n').map(s => s.trim().toLowerCase()).filter(Boolean))])
const keywordsChanged = computed(() => settings.value && keywordList.value.join('\n') !== settings.value.bannedKeywords.join('\n'))

function fill(s) {
  settings.value = s
  keywordText.value = s.bannedKeywords.join('\n')
  const cats = {}
  roots.value.forEach(c => { cats[c.id] = s.categoryCommission[c.id] != null ? Number(s.categoryCommission[c.id]) : '' })
  fees.value = { commissionPercent: Number(s.commissionPercent), categoryCommission: cats, settlementHoldDays: s.settlementHoldDays }
  ops.value = { paymentWindowMinutes: s.paymentWindowMinutes, disputeResponseHours: s.disputeResponseHours }
  sec.value = { maxFailedLogins: s.maxFailedLogins, lockMinutes: s.lockMinutes, ipFailLimit: s.ipFailLimit, registerPerIp: s.registerPerIp,
    requireAdmin2fa: s.requireAdmin2fa }
}

async function save(body, key) {
  saving.value = true
  msg.value = {}
  error.value = ''
  try {
    fill((await saveAdminSettings(body)).data)
    if (key) msg.value = { [key]: 'Đã lưu ✓' }
  } catch (e) {
    error.value = extractError(e)
  } finally {
    saving.value = false
  }
}

const saveFees = () => save({
  commissionPercent: fees.value.commissionPercent,
  categoryCommission: Object.fromEntries(Object.entries(fees.value.categoryCommission).map(([k, v]) => [k, v === '' || v == null ? null : v])),
  settlementHoldDays: fees.value.settlementHoldDays
}, 'fees')
const saveOps = () => save({ ...ops.value }, 'ops')

onMounted(async () => {
  try {
    const [s, tree] = await Promise.all([adminSettings(), categoryTree()])
    roots.value = tree.data
    fill(s.data)
  } catch (e) {
    error.value = extractError(e)
  }
})
</script>

<style scoped>
.row-setting, .setting { display: flex; align-items: center; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--color-border); }
.row-setting > div:first-child, .setting > div { flex: 1; }
.row-setting p, .setting p, .keywords p, .cat-fees p { margin: 4px 0 0; font-size: 13px; color: var(--color-text-muted); line-height: 1.6; }
.num { display: flex; align-items: center; gap: 6px; font-size: 14px; white-space: nowrap; }
.num input { width: 90px; }
.cat-fees { padding: 14px 0; border-bottom: 1px solid var(--color-border); }
.cat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 8px; margin-top: 10px; }
.cat { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 6px 10px; border: 1px solid var(--color-border); border-radius: 6px; font-size: 14px; }
.cat.custom { border-color: #e0d8ff; background: #faf8ff; }
.actions { display: flex; justify-content: flex-end; align-items: center; gap: 12px; padding-top: 12px; }
.switch { width: 46px; height: 26px; border-radius: 999px; border: none; background: #ccc; position: relative; cursor: pointer; flex-shrink: 0; }
.switch span { position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 50%; background: white; transition: left 0.15s; }
.switch.on { background: #6941c6; }
.switch.on span { left: 23px; }
.keywords { padding: 14px 0 0; }
.keywords textarea { margin: 10px 0 0; width: 100%; font-family: inherit; max-width: 420px; display: block; }
.kw-actions { display: flex; align-items: center; gap: 12px; margin-top: 8px; max-width: 420px; justify-content: space-between; }
.faint { color: var(--color-text-faint); font-size: 12px; }
.ok { color: #1e9e4a; font-size: 13px; }
.error { color: #d33; font-size: 13px; }
</style>
