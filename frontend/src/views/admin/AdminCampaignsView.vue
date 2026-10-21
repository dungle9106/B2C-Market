<template>
  <div>
    <section class="sc-card">
      <div class="sc-card-head">
        <h2 class="sc-card-title">Chương Trình Khuyến Mãi</h2>
        <span class="sc-card-sub">Mỗi chương trình (VD: Siêu Sale 11.11) có trang riêng gom Shoppi Voucher, khung giờ Flash Sale và banner của chương trình.</span>
        <button type="button" class="btn sm add" @click="open(null)">+ Tạo chương trình</button>
      </div>

      <div v-if="loading" class="sc-empty">Đang tải...</div>
      <div v-else-if="!list.length" class="sc-empty">Chưa có chương trình nào</div>
      <div v-for="c in list" :key="c.id" class="camp" :style="{ '--theme': c.themeColor }">
        <div class="cover">
          <img v-if="c.bannerUrl" :src="c.bannerUrl" alt="" />
          <span v-else>{{ c.name }}</span>
        </div>
        <div class="body">
          <div class="title">
            <strong>{{ c.name }}</strong>
            <span class="st" :class="c.status">{{ STATUS[c.status] }}</span>
          </div>
          <div v-if="c.tagline" class="tagline">{{ c.tagline }}</div>
          <div class="meta">{{ formatDateTime(c.startAt) }} → {{ formatDateTime(c.endAt) }} · <router-link :to="`/campaigns/${c.slug}`" target="_blank">/campaigns/{{ c.slug }}</router-link></div>
          <div class="links">
            <span>🎟 {{ c.vouchers }} voucher</span>
            <span>⚡ {{ c.flashSales }} khung giờ Flash Sale</span>
            <span>🖼 {{ c.banners }} banner</span>
          </div>
        </div>
        <div class="actions">
          <button type="button" class="btn sm ghost" @click="open(c)">Sửa</button>
          <button type="button" class="btn sm ghost" @click="addVoucher = c.id">+ Voucher</button>
          <button type="button" class="link" @click="run(() => toggleCampaign(c.id))">{{ c.active ? 'Ẩn' : 'Hiện' }}</button>
          <button v-if="!c.vouchers && !c.flashSales && !c.banners" type="button" class="link danger" @click="confirm(`Xóa chương trình ${c.name}?`) && run(() => deleteCampaign(c.id))">Xóa</button>
        </div>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <BaseModal v-if="form" :title="form.id ? 'Sửa chương trình' : 'Tạo chương trình khuyến mãi'" :width="640" @close="form = null">
      <form id="camp-form" class="form" @submit.prevent="save">
        <label>Tên chương trình *<input v-model.trim="form.name" maxlength="120" placeholder="VD: Siêu Sale 11.11" @input="autoSlug" /></label>
        <label>Đường dẫn trang
          <div class="slug"><span>/campaigns/</span><input v-model.trim="form.slug" maxlength="80" @input="slugTouched = true" /></div>
        </label>
        <label>Khẩu hiệu<input v-model.trim="form.tagline" maxlength="200" placeholder="VD: Voucher 50% · Flash Sale 0h – 12h – 20h" /></label>
        <label>Thể lệ chương trình<textarea v-model="form.description" rows="4" maxlength="2000"></textarea></label>
        <div class="row">
          <label>Bắt đầu *<input v-model="form.startAt" type="datetime-local" /></label>
          <label>Kết thúc *<input v-model="form.endAt" type="datetime-local" /></label>
          <label>Màu chủ đề<input v-model="form.themeColor" type="color" class="color" /></label>
        </div>
        <label>Ảnh bìa (tỷ lệ khoảng 4:1)</label>
        <div class="upload">
          <img v-if="form.bannerUrl" :src="form.bannerUrl" alt="" />
          <label class="btn sm ghost">{{ uploading ? 'Đang tải...' : form.bannerUrl ? 'Đổi ảnh' : 'Tải ảnh lên' }}
            <input type="file" accept="image/*" hidden @change="upload" />
          </label>
          <button v-if="form.bannerUrl" type="button" class="link danger" @click="form.bannerUrl = null">Bỏ ảnh</button>
        </div>
        <label class="check"><input v-model="form.active" type="checkbox" /> Hiển thị cho người mua</label>
        <p v-if="formError" class="error">{{ formError }}</p>
      </form>
      <template #footer>
        <button type="button" class="btn ghost" @click="form = null">Hủy</button>
        <button type="submit" form="camp-form" class="btn" :disabled="saving || uploading">{{ saving ? 'Đang lưu...' : 'Lưu' }}</button>
      </template>
    </BaseModal>

    <AdminVoucherForm v-if="addVoucher" :campaign-id="addVoucher" @close="addVoucher = null" @saved="addVoucher = null; load()" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import AdminVoucherForm from '../../components/admin/AdminVoucherForm.vue'
import { adminCampaigns, createCampaign, updateCampaign, toggleCampaign, deleteCampaign } from '../../api/admin'
import { uploadFile } from '../../api/uploads'
import { formatDateTime, toLocalInput } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const STATUS = { RUNNING: 'Đang diễn ra', UPCOMING: 'Sắp diễn ra', ENDED: 'Đã kết thúc', HIDDEN: 'Đang ẩn' }
const list = ref([])
const loading = ref(true)
const error = ref('')
const form = ref(null)
const formError = ref('')
const saving = ref(false)
const uploading = ref(false)
const slugTouched = ref(false)
const addVoucher = ref(null)

const slugify = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80)
function autoSlug() {
  if (!slugTouched.value && !form.value.id) form.value.slug = slugify(form.value.name)
}

function open(c) {
  formError.value = ''
  slugTouched.value = !!c
  form.value = c ? { ...c, startAt: toLocalInput(c.startAt), endAt: toLocalInput(c.endAt) } : {
    name: '', slug: '', tagline: '', description: '', bannerUrl: null, themeColor: '#ee4d2d', active: true,
    startAt: toLocalInput(new Date(Date.now() + 86400000)), endAt: toLocalInput(new Date(Date.now() + 4 * 86400000))
  }
}

async function upload(e) {
  const file = e.target.files[0]
  e.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    form.value.bannerUrl = (await uploadFile(file)).data.url
  } catch (err) {
    formError.value = extractError(err)
  } finally {
    uploading.value = false
  }
}

async function save() {
  saving.value = true
  formError.value = ''
  try {
    const body = { ...form.value }
    await (body.id ? updateCampaign(body.id, body) : createCampaign(body))
    form.value = null
    load()
  } catch (e) {
    formError.value = extractError(e)
  } finally {
    saving.value = false
  }
}

async function load() {
  try {
    list.value = (await adminCampaigns()).data
  } catch (e) {
    error.value = extractError(e)
  } finally {
    loading.value = false
  }
}

async function run(fn) {
  error.value = ''
  try {
    await fn()
    load()
  } catch (e) {
    error.value = extractError(e)
  }
}

onMounted(load)
</script>

<style scoped>
.sc-card-head { position: relative; padding-right: 180px; }
.add { position: absolute; right: 0; top: 0; }
.camp { display: flex; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--color-border); align-items: center; }
.cover { width: 220px; height: 64px; border-radius: 6px; overflow: hidden; background: var(--theme); color: white; font-weight: 700;
  display: flex; align-items: center; justify-content: center; text-align: center; padding: 0 8px; flex-shrink: 0; }
.cover img { width: 100%; height: 100%; object-fit: cover; }
.body { flex: 1; min-width: 0; font-size: 13px; }
.title { display: flex; gap: 8px; align-items: center; font-size: 15px; }
.tagline { color: var(--theme); margin-top: 2px; }
.meta { color: var(--color-text-muted); margin-top: 4px; }
.meta a { color: #2673dd; }
.links { display: flex; gap: 14px; margin-top: 4px; color: var(--color-text-muted); }
.st { font-size: 12px; padding: 2px 8px; border-radius: 3px; background: #f2f2f2; color: #666; }
.st.RUNNING { background: #eaf7ef; color: #1e9e4a; }
.st.UPCOMING { background: #e8f1fd; color: #2673dd; }
.st.HIDDEN { background: #fff4e5; color: #b35c00; }
.actions { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; justify-content: flex-end; max-width: 240px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; }
.link.danger { color: #d33; }
.form { display: flex; flex-direction: column; gap: 10px; font-size: 13px; }
.form > label { display: flex; flex-direction: column; gap: 4px; color: var(--color-text-muted); }
.row { display: grid; grid-template-columns: 1fr 1fr 110px; gap: 10px; }
.row label { display: flex; flex-direction: column; gap: 4px; color: var(--color-text-muted); }
.color { height: 38px; padding: 2px; }
.slug { display: flex; align-items: center; gap: 4px; }
.slug span { color: var(--color-text-faint); }
.upload { display: flex; align-items: center; gap: 10px; }
.upload img { height: 56px; max-width: 240px; object-fit: cover; border-radius: 4px; border: 1px solid var(--color-border); }
.check { flex-direction: row !important; align-items: center; }
.check input { width: auto; }
.error { color: #d33; font-size: 13px; }
@media (max-width: 760px) { .camp { flex-direction: column; align-items: stretch; } .cover { width: 100%; } .sc-card-head { padding-right: 0; } .add { position: static; } }
</style>
