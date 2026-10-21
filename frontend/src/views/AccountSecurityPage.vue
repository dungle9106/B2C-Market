<template>
  <!-- the security page inside the seller centre and the admin console -->
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Bảo Mật Tài Khoản</h2>
      <span class="sc-card-sub">Mật khẩu, xác thực 2 lớp, thiết bị đang đăng nhập và lịch sử đăng nhập</span>
    </div>
    <SecurityPanel :setup-required="!!route.query.required" @changed="onChanged" />
  </section>
</template>

<script setup>
import { useRoute } from 'vue-router'
import SecurityPanel from '../components/account/SecurityPanel.vue'
import { useAdminMe } from '../composables/useAdminMe'

const route = useRoute()
// an admin who just turned 2FA on may use the console again: reload the admin profile
const onChanged = () => { if (route.path.startsWith('/admin')) useAdminMe().load(true).catch(() => {}) }
</script>
