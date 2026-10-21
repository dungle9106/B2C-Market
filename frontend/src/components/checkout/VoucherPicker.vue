<template>
  <BaseModal :title="title" :width="560" @close="$emit('close')">
    <form class="code-row" novalidate @submit.prevent="applyTyped">
      <span>Mã Voucher</span>
      <input v-model.trim="typed" placeholder="Nhập mã voucher" maxlength="20" />
      <button class="btn" type="submit" :disabled="!typed">ÁP DỤNG</button>
    </form>

    <p v-if="!vouchers.length" class="empty">Hiện chưa có voucher nào khả dụng.</p>
    <ul class="list">
      <li v-for="v in decorated" :key="v.id">
        <label class="voucher" :class="{ disabled: v.reason }">
          <div class="stub" :class="v.scope === 'PLATFORM' ? 'platform' : 'shop'">
            <span class="stub-icon">{{ v.type === 'SHIPPING' ? '🚚' : '%' }}</span>
            <span class="stub-text">{{ v.scope === 'PLATFORM' ? 'SHOPPI' : v.shopName }}</span>
          </div>
          <div class="info">
            <div class="v-title">{{ v.title }}</div>
            <div class="v-min">Đơn tối thiểu {{ formatVnd(v.minOrder || 0) }}</div>
            <div class="v-meta">
              <span class="v-code">{{ v.code }}</span>
              <span v-if="v.endAt">· HSD: {{ formatDate(v.endAt) }}</span>
              <span v-if="v.usageLimit" class="left">· Còn {{ Math.max(0, v.usageLimit - v.usedCount) }} lượt</span>
            </div>
            <div v-if="v.reason" class="reason">{{ v.reason }}</div>
          </div>
          <input type="radio" name="voucher" :value="v.code" :checked="picked === v.code" :disabled="!!v.reason" @change="picked = v.code" />
        </label>
      </li>
    </ul>

    <template #footer>
      <button v-if="current" type="button" class="btn ghost" @click="$emit('apply', null)">Bỏ chọn voucher</button>
      <button type="button" class="btn ghost" @click="$emit('close')">Trở lại</button>
      <button type="button" class="btn" :disabled="!picked || picked === current" @click="$emit('apply', picked)">OK</button>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseModal from '../BaseModal.vue'
import { formatVnd } from '../../utils/format'

const props = defineProps({
  title: { type: String, default: 'Chọn Voucher' },
  vouchers: { type: Array, default: () => [] },
  amount: { type: Number, default: 0 },          // merchandise amount the min-order rule is checked against
  current: { type: String, default: null }        // applied code
})
const emit = defineEmits(['close', 'apply'])

const picked = ref(props.current)
const typed = ref('')

// grey out what can't be used, with the reason, like Shopee
const decorated = computed(() => props.vouchers.map(v => {
  let reason = ''
  if (v.perUserLimit && v.usedByMe >= v.perUserLimit) reason = 'Bạn đã dùng hết lượt của voucher này'
  else if (v.minOrder && props.amount < v.minOrder) reason = `Mua thêm ${formatVnd(v.minOrder - props.amount)} để sử dụng voucher`
  return { ...v, reason }
}))

const formatDate = (d) => new Date(d).toLocaleDateString('vi-VN')

// typed codes are validated by the checkout preview, which reports any problem
function applyTyped() {
  emit('apply', typed.value.toUpperCase())
}
</script>

<style scoped>
.code-row { display: flex; align-items: center; gap: 12px; background: #f8f8f8; padding: 14px 16px; margin-bottom: 16px; }
.code-row span { white-space: nowrap; font-size: 14px; }
.code-row input { margin: 0; text-transform: uppercase; }
.empty { text-align: center; color: var(--color-text-faint); padding: 24px 0; }
.list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
.voucher { display: flex; align-items: stretch; border: 1px solid #eee; border-radius: 3px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06); cursor: pointer; overflow: hidden; }
.voucher.disabled { cursor: not-allowed; opacity: 0.55; }
.stub { width: 100px; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; color: white; padding: 10px 6px; text-align: center; border-right: 2px dashed white; }
.stub.platform { background: var(--color-primary); }
.stub.shop { background: #26aa99; }
.stub-icon { width: 40px; height: 40px; border-radius: 50%; border: 2px solid rgba(255, 255, 255, 0.8); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 18px; }
.stub-text { font-size: 11px; font-weight: 600; max-width: 88px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info { flex: 1; min-width: 0; padding: 10px 14px; font-size: 13px; }
.v-title { font-size: 15px; font-weight: 500; margin-bottom: 2px; }
.v-min { color: var(--color-text-muted); }
.v-meta { color: var(--color-text-faint); font-size: 12px; margin-top: 6px; }
.v-code { font-weight: 600; color: var(--color-text-muted); }
.reason { color: #d33; font-size: 12px; margin-top: 4px; }
.voucher input { width: 18px; margin: auto 16px; accent-color: var(--color-primary); }
</style>
