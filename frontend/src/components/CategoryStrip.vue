<template>
  <div>
    <div class="category-grid">
      <router-link
        v-for="(c, i) in categories"
        :key="c.id"
        :to="{ name: 'category', params: { id: c.id } }"
        class="category-item"
      >
        <span class="icon-circle" :style="{ background: tint(i) }">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path :d="iconFor(c.name)" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </span>
        <span>{{ c.name }}</span>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listCategories } from '../api/products'

const categories = ref([])

// simple line icons drawn for Shoppi, picked by words in the category name (no third-party artwork)
const ICONS = [
  [['thoi trang', 'quan ao', 'ao ', 'vay', 'dam'], 'M8 4 4 7l2 3 2-1v11h8V9l2 1 2-3-4-3c-1 1.5-2.4 2-4 2s-3-.5-4-2Z'],
  [['dien thoai', 'phu kien'], 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1ZM11 18h2'],
  [['may tinh', 'laptop', 'dien tu', 'thiet bi'], 'M5 6h14v9H5ZM3 18h18'],
  [['nha cua', 'doi song', 'gia dung', 'noi that', 'nha bep'], 'M4 11l8-7 8 7M6 10v10h12V10M10 20v-5h4v5'],
  [['lam dep', 'my pham', 'trang diem'], 'M10 3h4v6h-4ZM9 9h6v12H9Z'],
  [['suc khoe', 'thuoc', 'y te'], 'M10 4h4v6h6v4h-6v6h-4v-6H4v-4h6Z'],
  [['sach', 'van phong', 'hoc tap'], 'M4 5h7v15H4ZM13 5h7v15h-7Z'],
  [['the thao', 'du lich', 'da ngoai'], 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18'],
  [['game', 'do choi', 'giai tri'], 'M7 9h10a4 4 0 0 1 0 8l-2-2H9l-2 2a4 4 0 0 1 0-8ZM8 11v4M6 13h4M16 12h.01M17 14h.01'],
  [['me va be', 'tre em', 'em be'], 'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM6 21c0-3.5 2.7-6 6-6s6 2.5 6 6'],
  [['thuc pham', 'do an', 'bach hoa', 'do uong'], 'M5 11h14l-1.5 9h-11ZM9 11V8a3 3 0 0 1 6 0v3'],
  [['dong ho', 'trang suc', 'phu kien thoi trang'], 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10ZM9 3h6l1 4M9 21h6l1-4M12 10v2l1.5 1.5'],
  [['giay', 'dep'], 'M3 16c0-2 1-6 3-7l3 2c2 1 5 1 8 2a4 4 0 0 1 4 3v2H3Z'],
  [['tui', 'vi', 'balo'], 'M6 8h12l-1 12H7ZM9 8a3 3 0 0 1 6 0'],
  [['o to', 'xe may', 'xe dap'], 'M5 16h14v-4l-2-4H7l-2 4ZM8 19a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3ZM16 19a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z'],
  [['thu cung'], 'M8 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM16 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM5 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM19 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM12 12c-3 0-5 3-5 5a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2c0-2-2-5-5-5Z']
]
const DEFAULT_ICON = 'M4 4h7v7H4ZM13 4h7v7h-7ZM4 13h7v7H4ZM13 13h7v7h-7Z'
const TINTS = ['#fff1ec', '#eef6ff', '#eefaf3', '#fff8e6', '#f6efff', '#ffeef3']

const plain = (s) => ' ' + s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'd').toLowerCase() + ' '
function iconFor(name) {
  const n = plain(name)
  return (ICONS.find(([words]) => words.some(w => n.includes(w.endsWith(' ') ? ' ' + w : w))) || [null, DEFAULT_ICON])[1]
}
const tint = (i) => TINTS[i % TINTS.length]

onMounted(async () => {
  const { data } = await listCategories()
  categories.value = data
})
</script>

<style scoped>
.icon-circle { color: var(--color-primary); transition: transform 0.15s; }
.category-item:hover .icon-circle { transform: translateY(-2px); }
</style>
