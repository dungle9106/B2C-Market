<template>
  <section class="sc-card">
    <router-link :to="{ name: 'seller-support' }" class="back">‹ Tất cả yêu cầu hỗ trợ</router-link>
    <TicketThread :ticket-id="Number(route.params.id)" />
  </section>
</template>

<script setup>
import { useRoute } from 'vue-router'
import TicketThread from '../../components/support/TicketThread.vue'

const route = useRoute()
</script>

<style scoped>
.back { display: inline-block; margin-bottom: 14px; color: var(--color-text-muted); font-size: 14px; }
</style>
