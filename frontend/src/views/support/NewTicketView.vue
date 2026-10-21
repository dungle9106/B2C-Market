<template>
  <div>
    <AppHeader />
    <div class="container page">
      <router-link to="/help" class="back">‹ Trung tâm hỗ trợ</router-link>
      <section class="shopee-section">
        <h1 class="title">Gửi yêu cầu hỗ trợ</h1>
        <p class="sub">Mô tả càng chi tiết, Shoppi càng hỗ trợ bạn nhanh hơn. Bạn sẽ nhận được thông báo khi có phản hồi.</p>
        <TicketForm :category="route.query.category || null" :order-id="route.query.orderId ? Number(route.query.orderId) : null" @created="onCreated">
          <template #cancel>
            <router-link to="/help" class="btn ghost">Hủy</router-link>
          </template>
        </TicketForm>
      </section>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import TicketForm from '../../components/support/TicketForm.vue'

const route = useRoute()
const router = useRouter()

function onCreated(ticket) {
  router.replace(`/help/tickets/${ticket.id}`)
}
</script>

<style scoped>
.page { max-width: 760px; padding-bottom: 40px; }
.back { display: inline-block; margin: 16px 0 0; color: var(--color-text-muted); }
.title { font-size: 20px; font-weight: 500; margin: 0 0 4px; }
.sub { font-size: 13px; color: var(--color-text-faint); margin: 0 0 18px; }
</style>
