<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Danh Mục Của Shop</h2>
      <span class="sc-card-sub">Nhóm sản phẩm theo cách của bạn — hiển thị thành các tab trên trang Shop</span>
    </div>

    <form class="add-row" novalidate @submit.prevent="onCreate">
      <input v-model.trim="newName" maxlength="60" placeholder="Tên danh mục mới, VD: Hàng mới về, Áo khoác, Sale cuối mùa" />
      <button class="btn" type="submit" :disabled="!newName || busy">+ Thêm danh mục</button>
    </form>
    <p v-if="error" class="err">{{ error }}</p>

    <div v-if="loading" class="sc-empty">Đang tải...</div>
    <div v-else-if="!list.length" class="sc-empty">Shop chưa có danh mục nào.</div>
    <ul v-else class="cats">
      <li v-for="(c, i) in list" :key="c.id" :class="{ open: openId === c.id }">
        <div class="cat-row">
          <div class="order">
            <button type="button" :disabled="i === 0 || busy" aria-label="Lên" @click="move(i, -1)">▲</button>
            <button type="button" :disabled="i === list.length - 1 || busy" aria-label="Xuống" @click="move(i, 1)">▼</button>
          </div>
          <template v-if="editingId === c.id">
            <input v-model.trim="editName" maxlength="60" class="rename" @keydown.enter.prevent="saveRename(c)" @keydown.esc="editingId = null" />
            <button type="button" class="btn sm" :disabled="!editName || busy" @click="saveRename(c)">Lưu</button>
            <button type="button" class="btn sm ghost" @click="editingId = null">Hủy</button>
          </template>
          <template v-else>
            <span class="name">{{ c.name }}</span>
            <span class="count">{{ c.productCount }} sản phẩm</span>
            <button type="button" class="link" @click="toggleOpen(c)">{{ openId === c.id ? 'Đóng' : 'Chọn sản phẩm' }}</button>
            <button type="button" class="link" @click="startRename(c)">Đổi tên</button>
            <button type="button" class="link danger" @click="onDelete(c)">Xóa</button>
          </template>
        </div>

        <!-- pick which products belong to this category -->
        <div v-if="openId === c.id" class="picker">
          <input v-model.trim="filter" placeholder="Lọc sản phẩm theo tên" class="filter" />
          <div class="product-grid">
            <label v-for="p in visibleProducts" :key="p.id" class="pick" :class="{ on: picked.has(p.id) }">
              <input type="checkbox" :checked="picked.has(p.id)" @change="picked.has(p.id) ? picked.delete(p.id) : picked.add(p.id)" />
              <img :src="p.thumbnailUrl || placeholder" alt="" />
              <span>{{ p.name }}</span>
            </label>
          </div>
          <div class="picker-actions">
            <span class="text-faint">Đã chọn {{ picked.size }} sản phẩm</span>
            <button type="button" class="btn sm" :disabled="busy" @click="saveProducts(c)">Lưu</button>
          </div>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import {
  myShopCategories, createShopCategory, renameShopCategory, deleteShopCategory, reorderShopCategories,
  assignShopCategory, myProducts
} from '../../api/products'
import { extractError } from '../../utils/apiError'

const list = ref([])
const products = ref([])
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const newName = ref('')
const editingId = ref(null)
const editName = ref('')
const openId = ref(null)
const filter = ref('')
const picked = reactive(new Set())
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23f0f0f0"/></svg>'

const visibleProducts = computed(() => {
  const q = filter.value.toLowerCase()
  return products.value.filter(p => !q || p.name.toLowerCase().includes(q))
})

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = extractError(e)
  } finally {
    busy.value = false
  }
}

async function load() {
  const [{ data: cats }, { data: prods }] = await Promise.all([myShopCategories(), myProducts()])
  list.value = cats
  products.value = prods
}

const onCreate = () => run(async () => {
  await createShopCategory(newName.value)
  newName.value = ''
  await load()
})

function startRename(c) {
  editingId.value = c.id
  editName.value = c.name
}
const saveRename = (c) => run(async () => {
  await renameShopCategory(c.id, editName.value)
  editingId.value = null
  await load()
})

const onDelete = (c) => {
  if (!confirm(`Xóa danh mục "${c.name}"? Sản phẩm trong danh mục vẫn được giữ nguyên.`)) return
  run(async () => {
    await deleteShopCategory(c.id)
    if (openId.value === c.id) openId.value = null
    await load()
  })
}

const move = (i, dir) => run(async () => {
  const ids = list.value.map(c => c.id)
  ;[ids[i], ids[i + dir]] = [ids[i + dir], ids[i]]
  const { data } = await reorderShopCategories(ids)
  list.value = data
})

function toggleOpen(c) {
  if (openId.value === c.id) return (openId.value = null)
  openId.value = c.id
  filter.value = ''
  picked.clear()
  products.value.filter(p => (p.shopCategoryIds || []).includes(c.id)).forEach(p => picked.add(p.id))
}

// sends the difference: newly ticked are added, unticked removed
const saveProducts = (c) => run(async () => {
  const before = new Set(products.value.filter(p => (p.shopCategoryIds || []).includes(c.id)).map(p => p.id))
  const add = [...picked].filter(id => !before.has(id))
  const remove = [...before].filter(id => !picked.has(id))
  if (add.length) await assignShopCategory(c.id, add, true)
  if (remove.length) await assignShopCategory(c.id, remove, false)
  openId.value = null
  await load()
})

onMounted(async () => {
  try {
    await load()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.add-row { display: flex; gap: 10px; margin-bottom: 8px; }
.add-row input { margin: 0; max-width: 520px; }
.err { color: #d33; font-size: 13px; }
.cats { list-style: none; margin: 12px 0 0; padding: 0; border: 1px solid var(--color-border); border-radius: 4px; }
.cats li { border-bottom: 1px solid var(--color-border); }
.cats li:last-child { border-bottom: none; }
.cats li.open { background: #fffbfa; }
.cat-row { display: flex; align-items: center; gap: 14px; padding: 12px 14px; }
.order { display: flex; flex-direction: column; }
.order button { background: none; border: none; font-size: 10px; color: var(--color-text-faint); cursor: pointer; padding: 1px 4px; line-height: 1; }
.order button:disabled { opacity: 0.25; cursor: default; }
.name { font-weight: 500; flex: 1; min-width: 0; }
.count { font-size: 13px; color: var(--color-text-faint); }
.rename { margin: 0; flex: 1; max-width: 360px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0; }
.link.danger { color: #d33; }
.link:hover { text-decoration: underline; }
.picker { padding: 0 14px 14px 48px; }
.filter { max-width: 320px; margin-bottom: 10px; }
.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 8px; max-height: 360px; overflow-y: auto; }
.pick { display: flex; align-items: center; gap: 8px; border: 1px solid var(--color-border); border-radius: 4px; padding: 6px 8px; background: white; cursor: pointer; font-size: 13px; }
.pick.on { border-color: var(--color-primary); }
.pick input { width: auto; margin: 0; accent-color: var(--color-primary); }
.pick img { width: 32px; height: 32px; object-fit: cover; border-radius: 3px; }
.pick span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-actions { display: flex; justify-content: flex-end; align-items: center; gap: 12px; margin-top: 10px; font-size: 13px; }
</style>
