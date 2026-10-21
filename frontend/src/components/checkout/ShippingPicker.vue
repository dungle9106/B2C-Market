<template>
  <BaseModal title="Chọn phương thức vận chuyển" :width="560" @close="$emit('close')">
    <p class="hint">Phí vận chuyển tính theo khoảng cách từ {{ shopProvince || 'shop' }} đến địa chỉ nhận hàng.</p>
    <div v-for="group in grouped" :key="group.method" class="group">
      <h3>{{ group.name }}</h3>
      <label v-for="o in group.options" :key="o.code" class="option" :class="{ active: picked === o.code }">
        <input type="radio" name="shipping" :value="o.code" :checked="picked === o.code" @change="picked = o.code" />
        <div class="info">
          <div class="carrier">{{ o.carrierName }}</div>
          <div class="eta">{{ etaText(o) }}</div>
        </div>
        <span class="fee">{{ formatVnd(o.fee) }}</span>
      </label>
    </div>

    <template #footer>
      <button type="button" class="btn ghost" @click="$emit('close')">Trở lại</button>
      <button type="button" class="btn" @click="$emit('select', picked)">Hoàn thành</button>
    </template>
  </BaseModal>
</template>

<script setup>
import { ref, computed } from 'vue'
import BaseModal from '../BaseModal.vue'
import { formatVnd } from '../../utils/format'

const props = defineProps({
  options: { type: Array, required: true },   // ShippingService options for one shop
  current: { type: String, default: null },
  shopProvince: { type: String, default: null }
})
defineEmits(['close', 'select'])

const picked = ref(props.current)

const ORDER = ['SAME_DAY', 'EXPRESS', 'ECONOMY']
const grouped = computed(() => ORDER
  .map(m => ({ method: m, options: props.options.filter(o => o.method === m) }))
  .filter(g => g.options.length)
  .map(g => ({ ...g, name: g.options[0].methodName })))

const fmt = (d) => new Date(d).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
function etaText(o) {
  if (o.method === 'SAME_DAY') return 'Nhận hàng trong ngày hôm nay'
  return o.etaFrom === o.etaTo ? `Nhận hàng ngày ${fmt(o.etaFrom)}` : `Nhận hàng từ ${fmt(o.etaFrom)} - ${fmt(o.etaTo)}`
}
</script>

<style scoped>
.hint { font-size: 13px; color: var(--color-text-muted); margin: 0 0 12px; }
.group h3 { font-size: 14px; font-weight: 600; margin: 14px 0 8px; color: var(--color-text-muted); }
.option { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--color-border); border-radius: 3px; margin-bottom: 8px; cursor: pointer; }
.option.active { border-color: var(--color-primary); background: #fffaf8; }
.option input { width: 18px; margin: 0; accent-color: var(--color-primary); }
.info { flex: 1; }
.carrier { font-size: 14px; font-weight: 500; }
.eta { font-size: 12px; color: #26aa99; margin-top: 2px; }
.fee { font-size: 15px; }
</style>
