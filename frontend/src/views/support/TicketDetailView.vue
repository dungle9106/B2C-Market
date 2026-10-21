<template>
  <div>
    <AppHeader />
    <div class="container page">
      <router-link to="/help" class="back">‹ Trung tâm hỗ trợ</router-link>
      <section class="shopee-section">
        <TicketThread :ticket-id="Number(route.params.id)" :order-links="auth.isBuyer" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import TicketThread from '../../components/support/TicketThread.vue'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const auth = useAuthStore()
</script>

<style scoped>
.page { max-width: 860px; padding-bottom: 40px; }
.back { display: inline-block; margin: 16px 0 0; color: var(--color-text-muted); }
</style>
