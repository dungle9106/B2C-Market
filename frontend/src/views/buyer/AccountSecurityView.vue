<template>
  <div>
    <AppHeader />
    <div class="container page">
      <h2 class="title">Bảo mật tài khoản</h2>
      <SecurityPanel />
    </div>
  </div>
</template>

<script setup>
import AppHeader from '../../components/AppHeader.vue'
import SecurityPanel from '../../components/account/SecurityPanel.vue'
</script>

<style scoped>
.page { max-width: 860px; padding: 20px 16px 40px; }
.title { margin: 0 0 14px; font-size: 20px; }
</style>
