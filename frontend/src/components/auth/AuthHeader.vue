<template>
  <!-- white top bar of the login/signup pages: logo + page title, like Shopee -->
  <header class="auth-header">
    <div class="container auth-header-inner">
      <router-link to="/" class="brand">
        <span class="logo-mark">S</span>
        <span class="logo-text">Shoppi</span>
      </router-link>
      <span class="page-title">{{ title }}</span>
      <a href="#" class="help-link" @click.prevent>Bạn cần giúp đỡ?</a>
    </div>
  </header>
</template>

<script setup>
defineProps({ title: { type: String, required: true } })
</script>

<style scoped>
.auth-header { background: var(--color-surface); box-shadow: 0 6px 6px rgba(0, 0, 0, 0.06); position: relative; z-index: 1; }
.auth-header-inner { display: flex; align-items: center; gap: 16px; height: 84px; }
.brand { display: flex; align-items: center; gap: 8px; color: var(--color-primary); }
.logo-mark {
  width: 38px; height: 38px; border-radius: 8px;
  background: var(--color-primary); color: white;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 20px;
}
.logo-text { font-weight: 700; font-size: 24px; }
.page-title { font-size: 24px; color: var(--color-text); }
.help-link { margin-left: auto; color: var(--color-primary); font-size: 14px; }

@media (max-width: 480px) {
  .auth-header-inner { height: 64px; }
  .logo-text, .page-title { font-size: 18px; }
  .help-link { display: none; }
}
</style>
