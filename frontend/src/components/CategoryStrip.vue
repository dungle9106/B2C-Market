<template>
  <div class="cat-strip">
    <button v-show="canLeft" type="button" class="cat-arrow left" aria-label="Danh mục trước" @click="scrollBy(-1)">‹</button>
    <div ref="track" class="cat-track" @scroll="updateArrows">
      <router-link
        v-for="(c, i) in categories"
        :key="c.id"
        :to="{ name: 'category', params: { id: c.id } }"
        class="cat-cell"
      >
        <span class="icon-circle" :style="{ background: tint(i) }">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path :d="iconFor(c.name)" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
        </span>
        <span class="cat-name">{{ c.name }}</span>
      </router-link>
    </div>
    <button v-show="canRight" type="button" class="cat-arrow right" aria-label="Danh mục tiếp" @click="scrollBy(1)">›</button>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { listCategories } from '../api/products'

const categories = ref([])
const track = ref(null)
const canLeft = ref(false)
const canRight = ref(false)

// simple line icons drawn for Shoppi, picked by words in the category name (no third-party artwork)
const ICONS = [
  [['may anh', 'may quay', 'camera'], 'M4 8h4l2-3h4l2 3h4v11H4ZM12 10a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z'],
  [['dien gia dung'], 'M6 20h12l-1-11H7ZM9 9V6a3 3 0 0 1 6 0v3M17 11h2a2 2 0 0 1 0 4h-2'],
  [['trang suc'], 'M7 4h10l4 5-9 11L3 9ZM3 9h18M10 4l2 16 2-16'],
  [['giat giu', 'cham soc nha cua'], 'M9 3h6v3l2 3v12H7V9l2-3ZM7 13h10'],
  [['dung cu & thiet bi', 'tien ich'], 'M14.5 4.5a4 4 0 0 0 5 5L10 19a2.1 2.1 0 0 1-3-3l9.5-9.5a4 4 0 0 1-2-2Z'],
  [['tre em'], 'M9 4 5 6l1 4 2-1v10h8V9l2 1 1-4-4-2a3 3 0 0 1-6 0Z'],
  [['thoi trang', 'quan ao', 'ao ', 'vay', 'dam'], 'M8 4 4 7l2 3 2-1v11h8V9l2 1 2-3-4-3c-1 1.5-2.4 2-4 2s-3-.5-4-2Z'],
  [['dien thoai', 'phu kien'], 'M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1ZM11 18h2'],
  [['may tinh', 'laptop', 'dien tu', 'thiet bi'], 'M5 6h14v9H5ZM3 18h18'],
  [['nha cua', 'doi song', 'gia dung', 'noi that', 'nha bep'], 'M4 11l8-7 8 7M6 10v10h12V10M10 20v-5h4v5'],
  [['lam dep', 'my pham', 'trang diem'], 'M10 3h4v6h-4ZM9 9h6v12H9Z'],
  [['suc khoe', 'thuoc', 'y te'], 'M10 4h4v6h6v4h-6v6h-4v-6H4v-4h6Z'],
  [['sach', 'van phong', 'hoc tap'], 'M4 5h7v15H4ZM13 5h7v15h-7Z'],
  [['the thao', 'du lich', 'da ngoai'], 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18'],
  [['game', 'do choi', 'giai tri'], 'M7 9h10a4 4 0 0 1 0 8l-2-2H9l-2 2a4 4 0 0 1 0-8ZM8 11v4M6 13h4M16 12h.01M17 14h.01'],
  [['me va be', 'me & be', 'em be'], 'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM6 21c0-3.5 2.7-6 6-6s6 2.5 6 6'],
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

// 2 rows that scroll sideways; the arrows move one visible page at a time
function updateArrows() {
  const el = track.value
  if (!el) return
  canLeft.value = el.scrollLeft > 4
  canRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 4
}
function scrollBy(dir) {
  const el = track.value
  if (el) el.scrollBy({ left: dir * el.clientWidth, behavior: 'smooth' })
}

onMounted(async () => {
  window.addEventListener('resize', updateArrows)
  const { data } = await listCategories()
  categories.value = data
  await nextTick()
  updateArrows()
})
onBeforeUnmount(() => window.removeEventListener('resize', updateArrows))
</script>

<style scoped>
.cat-strip { position: relative; }
.cat-track {
  display: grid;
  grid-template-rows: repeat(2, auto);
  grid-auto-flow: column;
  grid-auto-columns: calc(100% / 10);
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  border-top: 1px solid #f0f0f0;
  border-left: 1px solid #f0f0f0;
}
.cat-track::-webkit-scrollbar { display: none; }
.cat-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 6px 12px;
  min-height: 132px;
  text-align: center;
  border-right: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
  scroll-snap-align: start;
  transition: box-shadow 0.15s;
}
.cat-cell:hover { box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.06), 0 2px 8px rgba(0, 0, 0, 0.08); position: relative; z-index: 1; }
.icon-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  flex-shrink: 0;
  transition: transform 0.15s;
}
.cat-cell:hover .icon-circle { transform: translateY(-2px); }
.cat-name {
  font-size: 13px;
  line-height: 1.35;
  color: var(--color-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.cat-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #fff;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.18);
  font-size: 22px;
  line-height: 1;
  color: #555;
  cursor: pointer;
}
.cat-arrow:hover { color: var(--color-primary); }
.cat-arrow.left { left: -18px; }
.cat-arrow.right { right: -18px; }
@media (max-width: 900px) { .cat-track { grid-auto-columns: calc(100% / 6); } }
@media (max-width: 600px) {
  .cat-track { grid-auto-columns: calc(100% / 4.5); }
  .icon-circle { width: 48px; height: 48px; }
  .cat-cell { min-height: 108px; }
  .cat-name { font-size: 12px; }
  .cat-arrow { display: none; }
}
</style>
