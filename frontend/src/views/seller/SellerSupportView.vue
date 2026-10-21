<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">{{ creating ? 'Gửi yêu cầu hỗ trợ' : 'Hỗ Trợ Từ Shoppi' }}</h2>
      <span class="sc-card-sub">{{ creating ? 'Đội ngũ Shoppi phản hồi trong vòng 24 giờ' : 'Các yêu cầu shop đã gửi tới đội ngũ Shoppi' }}</span>
      <button v-if="!creating" type="button" class="btn sm new" @click="setCreating(true)">+ Tạo yêu cầu</button>
    </div>

    <TicketForm v-if="creating" @created="t => router.push(`/seller/support/${t.id}`)">
      <template #cancel>
        <button type="button" class="btn ghost" @click="setCreating(false)">Hủy</button>
      </template>
    </TicketForm>
    <TicketList v-else detail-base="/seller/support/" />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TicketForm from '../../components/support/TicketForm.vue'
import TicketList from '../../components/support/TicketList.vue'

const route = useRoute()
const router = useRouter()
// ?new=1 opens the form, so the help centre can link straight to it
const creating = computed(() => route.query.new === '1')
const setCreating = (on) => router.replace({ query: on ? { new: '1' } : {} })
</script>

<style scoped>
.new { margin-left: auto; }
</style>
