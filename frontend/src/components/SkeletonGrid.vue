<template>
  <!-- grey shapes of product cards while the real ones load, so the page does not jump when they arrive -->
  <div class="grid-products" aria-busy="true" aria-label="Đang tải sản phẩm">
    <div v-for="i in count" :key="i" class="sk-card">
      <div class="sk sk-img"></div>
      <div class="sk-body">
        <div class="sk sk-line"></div>
        <div class="sk sk-line short"></div>
        <div class="sk-foot"><div class="sk sk-price"></div><div class="sk sk-sold"></div></div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ count: { type: Number, default: 12 } })
</script>

<style scoped>
.sk-card { background: white; border-radius: var(--radius-sm); box-shadow: var(--shadow-card); overflow: hidden; }
.sk-img { aspect-ratio: 1; border-radius: 0; }
.sk-body { padding: 8px; display: flex; flex-direction: column; gap: 6px; }
.sk-line { height: 11px; }
.sk-line.short { width: 60%; }
.sk-foot { display: flex; justify-content: space-between; margin-top: 6px; }
.sk-price { height: 14px; width: 40%; }
.sk-sold { height: 10px; width: 25%; }
</style>
