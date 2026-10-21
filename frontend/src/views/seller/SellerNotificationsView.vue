<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Thông Báo</h2>
      <span class="sc-card-sub">Đơn hàng mới, yêu cầu trả hàng, tin nhắn của người mua và thông báo từ Shoppi</span>
    </div>
    <NotificationList />
  </section>
</template>

<script setup>
import NotificationList from '../../components/NotificationList.vue'
</script>
