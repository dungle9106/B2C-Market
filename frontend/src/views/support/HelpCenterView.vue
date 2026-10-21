<template>
  <div>
    <AppHeader />
    <div class="hero">
      <div class="container hero-inner">
        <h1>Xin chào, Shoppi có thể giúp gì cho bạn?</h1>
        <input v-model.trim="query" type="search" placeholder="Nhập từ khóa, ví dụ: hủy đơn, hoàn tiền, phí ship..." />
      </div>
    </div>

    <div class="container page">
      <!-- quick channels -->
      <div class="channels">
        <router-link v-if="auth.isBuyer" to="/chat" class="channel">
          <span class="ch-icon">✨</span>
          <strong>Hỏi Trợ lý AI</strong>
          <span>Tư vấn sản phẩm, tra cứu đơn hàng 24/7</span>
        </router-link>
        <router-link v-if="auth.isBuyer" to="/messages" class="channel">
          <span class="ch-icon">💬</span>
          <strong>Chat với Shop</strong>
          <span>Hỏi trực tiếp người bán về sản phẩm, đơn hàng</span>
        </router-link>
        <router-link :to="newTicketLink" class="channel">
          <span class="ch-icon">📝</span>
          <strong>Gửi yêu cầu hỗ trợ</strong>
          <span>Đội ngũ Shoppi phản hồi trong vòng 24 giờ</span>
        </router-link>
      </div>

      <div class="layout">
        <!-- FAQ -->
        <section class="shopee-section faq">
          <h2 class="section-h">{{ query ? `Kết quả cho "${query}"` : 'Câu hỏi thường gặp' }}</h2>
          <nav v-if="!query" class="topics">
            <button v-for="t in FAQ" :key="t.topic" type="button" :class="{ active: topic === t.topic }" @click="topic = t.topic">
              <span>{{ t.icon }}</span> {{ t.topic }}
            </button>
          </nav>
          <div v-if="!visible.length" class="empty">
            Không tìm thấy câu trả lời phù hợp. <router-link :to="newTicketLink" class="text-primary">Gửi yêu cầu hỗ trợ</router-link>
          </div>
          <details v-for="f in visible" :key="f.q" class="qa">
            <summary>{{ f.q }}</summary>
            <p>{{ f.a }}</p>
          </details>
        </section>

        <!-- my tickets -->
        <aside class="shopee-section mine">
          <h2 class="section-h">Yêu cầu hỗ trợ của tôi</h2>
          <template v-if="!auth.isLoggedIn">
            <p class="text-muted small">Đăng nhập để gửi và theo dõi yêu cầu hỗ trợ.</p>
            <router-link :to="{ name: 'login', query: { redirect: '/help' } }" class="btn block">Đăng nhập</router-link>
          </template>
          <template v-else-if="auth.isSeller">
            <p class="text-muted small">Yêu cầu của shop được quản lý trong Kênh Người Bán.</p>
            <router-link to="/seller/support" class="btn block">Mở Hỗ trợ Người bán</router-link>
          </template>
          <template v-else-if="auth.isAdmin">
            <router-link to="/admin/support" class="btn block">Mở bàn xử lý yêu cầu</router-link>
          </template>
          <template v-else>
            <TicketList detail-base="/help/tickets/" />
            <router-link to="/help/new" class="btn block new-btn">+ Tạo yêu cầu mới</router-link>
          </template>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import AppHeader from '../../components/AppHeader.vue'
import TicketList from '../../components/support/TicketList.vue'
import { useAuthStore } from '../../stores/auth'
import { FAQ } from '../../utils/support'

const auth = useAuthStore()
const query = ref('')
const topic = ref(FAQ[0].topic)

const newTicketLink = computed(() => (auth.isSeller ? '/seller/support?new=1' : '/help/new'))

// accent-insensitive search over questions and answers
const fold = (s) => s.normalize('NFD').replace(/\p{M}/gu, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase()
const visible = computed(() => {
  if (!query.value) return FAQ.find(t => t.topic === topic.value)?.items || []
  const words = fold(query.value).split(/\s+/).filter(Boolean)
  return FAQ.flatMap(t => t.items).filter(f => {
    const text = fold(f.q + ' ' + f.a)
    return words.every(w => text.includes(w))
  })
})
</script>

<style scoped>
.hero { background: linear-gradient(135deg, var(--color-primary), #ff7337); color: white; padding: 36px 0 40px; }
.hero-inner { text-align: center; }
.hero h1 { font-size: 24px; font-weight: 500; margin: 0 0 18px; }
.hero input { max-width: 560px; margin: 0; padding: 12px 16px; font-size: 15px; border: none; border-radius: 3px; }
.page { padding-bottom: 40px; }

.channels { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin-top: -20px; }
.channel { background: white; border-radius: 6px; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08); padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; transition: transform 0.15s; }
.channel:hover { transform: translateY(-2px); }
.channel strong { font-size: 15px; }
.channel span:last-child { font-size: 13px; color: var(--color-text-muted); }
.ch-icon { font-size: 24px; }

.layout { display: grid; grid-template-columns: 1fr 360px; gap: 12px; align-items: start; }
.section-h { font-size: 17px; font-weight: 500; margin: 0 0 12px; }
.topics { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.topics button { background: white; border: 1px solid var(--color-border); border-radius: 999px; padding: 6px 14px; font-size: 13px; cursor: pointer; }
.topics button.active { border-color: var(--color-primary); color: var(--color-primary); background: var(--color-primary-light); }
.qa { border-bottom: 1px solid var(--color-border); padding: 14px 4px; }
.qa summary { cursor: pointer; font-weight: 500; }
.qa summary:hover { color: var(--color-primary); }
.qa p { margin: 10px 0 0; color: var(--color-text-muted); line-height: 1.6; }
.empty { color: var(--color-text-faint); padding: 24px 0; text-align: center; }
.small { font-size: 13px; }
.new-btn { margin-top: 14px; }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .channels { margin-top: 12px; }
}
</style>
