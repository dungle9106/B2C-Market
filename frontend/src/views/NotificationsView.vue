<template>
  <div>
    <AppHeader />
    <div class="container page">
      <section class="shopee-section">
        <h1 class="title">Thông Báo</h1>
        <PushToggle />
        <NotificationList />
      </section>
    </div>
  </div>
</template>

<script setup>
import AppHeader from '../components/AppHeader.vue'
import NotificationList from '../components/NotificationList.vue'
import PushToggle from '../components/PushToggle.vue'
</script>

<style scoped>
.page { padding-bottom: 40px; }
.title { font-size: 19px; font-weight: 500; margin: 4px 0 8px; }
</style>
