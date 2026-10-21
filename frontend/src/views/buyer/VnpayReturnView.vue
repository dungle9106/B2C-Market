<template>
  <!-- styled with Tailwind utilities (tw- prefix) -->
  <div class="auth-page">
    <AuthHeader title="Kết quả thanh toán" />
    <main class="tw-flex tw-justify-center tw-bg-neutral-100 tw-px-4 tw-py-12 tw-min-h-[calc(100vh-84px)]">
      <section class="auth-card tw-w-full tw-max-w-[440px] tw-text-center">
        <template v-if="state === 'loading'"><div class="tw-text-4xl">⏳</div><p>Đang xác nhận giao dịch với VNPay...</p></template>
        <template v-else-if="state === 'ok'">
          <div class="tw-text-4xl">✅</div>
          <h2 class="card-title">Thanh toán thành công</h2>
          <p class="tw-my-1 tw-text-2xl tw-font-bold tw-text-primary">{{ formatVnd(result.amount) }}</p>
          <p class="tw-text-sm tw-text-neutral-500">Mã giao dịch VNPay {{ result.transactionNo }}<span v-if="result.bankCode"> · {{ result.bankCode }}</span></p>
          <p class="tw-text-sm tw-text-neutral-500">Đơn hàng: {{ result.orders.map(o => o.code).join(', ') }}</p>
          <router-link :to="{ name: 'my-orders', query: { placed: result.checkoutCode } }" class="btn block lg">XEM ĐƠN HÀNG</router-link>
        </template>
        <template v-else-if="state === 'failed'">
          <div class="tw-text-4xl">⚠️</div>
          <h2 class="card-title">Thanh toán chưa thành công</h2>
          <p class="tw-text-sm tw-text-neutral-500">{{ result.message }}</p>
          <router-link v-if="result.canRetry" :to="{ name: 'payment', params: { code: result.checkoutCode } }" class="btn block lg">THỬ LẠI</router-link>
          <router-link :to="{ name: 'my-orders' }" class="tw-mt-3 tw-inline-block tw-text-sm tw-text-neutral-500">Đơn hàng của tôi</router-link>
        </template>
        <template v-else>
          <div class="tw-text-4xl">⚠️</div>
          <h2 class="card-title">Không xác nhận được giao dịch</h2>
          <p class="tw-text-sm tw-text-neutral-500">{{ error }}</p>
          <router-link :to="{ name: 'my-orders' }" class="btn block lg">ĐƠN HÀNG CỦA TÔI</router-link>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AuthHeader from '../../components/auth/AuthHeader.vue'
import { vnpayReturn } from '../../api/payments'
import { extractError } from '../../utils/apiError'
import { formatVnd } from '../../utils/format'

const route = useRoute()
const state = ref('loading')
const result = ref(null)
const error = ref('')

onMounted(async () => {
  try {
    // VNPay's signed parameters, checked by the server
    result.value = (await vnpayReturn(route.query)).data
    state.value = result.value.success ? 'ok' : 'failed'
  } catch (e) {
    error.value = extractError(e)
    state.value = 'error'
  }
})
</script>
