<template>
  <div>
    <AppHeader />
    <div class="container page">
      <ChatWorkspace side="buyer" />
    </div>
  </div>
</template>

<script setup>
import AppHeader from '../../components/AppHeader.vue'
import ChatWorkspace from '../../components/chat/ChatWorkspace.vue'
</script>

<style scoped>
.page { padding: 16px 16px 32px; }
</style>
