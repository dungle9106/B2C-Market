<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Hiệu Quả Hoạt Động</h2>
      <span class="sc-card-sub">Chỉ số vận hành 30 ngày gần nhất, điểm phạt (Sao Quả Tạ) và tiêu chí Shop Yêu Thích</span>
    </div>
    <div v-if="!report" class="sc-empty">Đang tải...</div>
    <PerformanceReport v-else :report="report" />
    <p class="tw-mt-4 tw-text-xs tw-text-neutral-400">
      Đơn không thành công: đơn do shop hủy. Giao hàng trễ: giao cho đơn vị vận chuyển sau thời gian chuẩn bị hàng của shop thêm 1 ngày.
      Mỗi tuần, nếu một trong hai tỷ lệ vượt ngưỡng shop bị cộng 1 điểm; sản phẩm bị gỡ do vi phạm bị cộng 1 điểm (hàng giả, hàng cấm: 2 điểm).
    </p>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PerformanceReport from '../../components/PerformanceReport.vue'
import { myPerformance } from '../../api/performance'

const report = ref(null)
onMounted(async () => { report.value = (await myPerformance()).data })
</script>
