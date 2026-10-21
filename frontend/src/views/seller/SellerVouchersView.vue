<template>
  <section class="sc-card">
    <VoucherManager
      title="Mã Giảm Giá Của Shop"
      subtitle="Áp dụng cho toàn shop hoặc một số sản phẩm. Voucher công khai hiển thị trên trang shop, trang sản phẩm và ở bước thanh toán."
      :api="{ list: myShopVouchers, create: createShopVoucher, toggle: toggleShopVoucher, update: updateShopVoucher, end: endShopVoucher }"
      shop-mode
    />
  </section>
</template>

<script setup>
import VoucherManager from '../../components/VoucherManager.vue'
import { myShopVouchers, createShopVoucher, toggleShopVoucher, updateShopVoucher, endShopVoucher } from '../../api/vouchers'
</script>
