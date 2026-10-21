<template>
  <div>
    <AppHeader />
    <div class="container page">
      <div v-if="loaded && !cart.items.length" class="empty">
        <svg width="96" height="96" viewBox="0 0 24 24" fill="none"><path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13 5.4 5M7 13l-2.3 2.3c-.4.4-.1 1.2.5 1.2H17M17 17a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM9 19a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <p>Giỏ hàng của bạn còn trống</p>
        <router-link to="/" class="btn lg">MUA NGAY</router-link>
      </div>

      <template v-else-if="loaded">
        <div class="cart-head grid-row">
          <label class="check-cell">
            <input type="checkbox" :checked="allSelected" :disabled="!selectable.length" @change="selectAll($event.target.checked)" />
            <span>Sản Phẩm</span>
          </label>
          <span class="center">Đơn Giá</span>
          <span class="center">Số Lượng</span>
          <span class="center">Số Tiền</span>
          <span class="center">Thao Tác</span>
        </div>

        <section v-for="group in cart.groupedByShop" :key="group.shopId" class="shop-card">
          <header class="shop-header">
            <label class="check-cell">
              <input type="checkbox" :checked="isShopSelected(group)" :disabled="!group.items.some(i => i.available)" @change="selectShop(group, $event.target.checked)" />
              <span class="shop-badge">Shop</span>
              <router-link :to="{ name: 'shop', params: { id: group.shopId } }" class="shop-name">{{ group.shopName }}</router-link>
            </label>
          </header>

          <div v-for="item in group.items" :key="item.id" class="grid-row item-row" :class="{ unavailable: !item.available }">
            <div class="check-cell product-cell">
              <input type="checkbox" :checked="cart.selectedIds.includes(item.id)" :disabled="!item.available" @change="cart.toggle(item.id, $event.target.checked)" />
              <router-link :to="`/products/${item.productId}`" class="product-link">
                <img :src="item.thumbnailUrl || placeholder" alt="" class="thumb" />
                <div class="product-text">
                  <div class="name">{{ item.productName }}</div>
                  <div v-if="item.freeShipping" class="freeship-tag">Freeship</div>
                </div>
              </router-link>
              <div v-if="item.variantName" class="variant">Phân loại: {{ item.variantName }}</div>
            </div>
            <div class="center price-cell">
              <span v-if="item.originalPrice" class="old-price">{{ formatVnd(item.originalPrice) }}</span>
              <span>{{ formatVnd(item.unitPrice) }}</span>
            </div>
            <div class="center qty-cell">
              <template v-if="item.available">
                <div class="stepper">
                  <button type="button" aria-label="Giảm" :disabled="busyId === item.id" @click="changeQty(item, item.quantity - 1)">−</button>
                  <input :value="item.quantity" type="number" min="1" :max="item.availableStock" aria-label="Số lượng"
                         @change="changeQty(item, Number($event.target.value))" />
                  <button type="button" aria-label="Tăng" :disabled="busyId === item.id || item.quantity >= item.availableStock" @click="changeQty(item, item.quantity + 1)">+</button>
                </div>
                <div v-if="item.availableStock <= 10" class="stock-hint">Còn {{ item.availableStock }} sản phẩm</div>
              </template>
              <span v-else class="unavailable-label">{{ item.availableStock <= 0 ? 'Hết hàng' : 'Ngừng bán' }}</span>
            </div>
            <div class="center line-total">{{ formatVnd(item.unitPrice * item.quantity) }}</div>
            <div class="center">
              <button type="button" class="link-btn" @click="onRemove(item)">Xóa</button>
            </div>
          </div>
        </section>

        <!-- sticky checkout bar -->
        <div class="checkout-bar">
          <label class="check-cell">
            <input type="checkbox" :checked="allSelected" :disabled="!selectable.length" @change="selectAll($event.target.checked)" />
            <span>Chọn Tất Cả ({{ selectable.length }})</span>
          </label>
          <button type="button" class="link-btn" :disabled="!cart.selectedItems.length" @click="onRemoveSelected">Xóa</button>
          <button v-if="unavailableIds.length" type="button" class="link-btn primary" @click="onRemoveUnavailable">Bỏ sản phẩm không hoạt động</button>

          <div class="summary">
            <div>
              Tổng thanh toán ({{ selectedQty }} sản phẩm):
              <strong class="total">{{ formatVnd(selectedTotal) }}</strong>
            </div>
            <div v-if="selectedSaving > 0" class="saving">Tiết kiệm <span>{{ formatVnd(selectedSaving) }}</span></div>
          </div>
          <button class="btn lg buy" :disabled="!cart.selectedItems.length" @click="goCheckout">Mua Hàng</button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import { useCartStore } from '../../stores/cart'
import { formatVnd } from '../../utils/format'
import { extractError } from '../../utils/apiError'

const route = useRoute()
const router = useRouter()
const cart = useCartStore()
const loaded = ref(false)
const busyId = ref(null)
const error = ref('')
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="%23f0f0f0"/></svg>'

const selectable = computed(() => cart.items.filter(i => i.available))
const unavailableIds = computed(() => cart.items.filter(i => !i.available).map(i => i.id))
const allSelected = computed(() => selectable.value.length > 0 && selectable.value.every(i => cart.selectedIds.includes(i.id)))
const selectedQty = computed(() => cart.selectedItems.reduce((s, i) => s + i.quantity, 0))
const selectedTotal = computed(() => cart.selectedItems.reduce((s, i) => s + i.unitPrice * i.quantity, 0))
const selectedSaving = computed(() => cart.selectedItems.reduce((s, i) => s + (i.originalPrice ? (i.originalPrice - i.unitPrice) * i.quantity : 0), 0))

function isShopSelected(group) {
  const avail = group.items.filter(i => i.available)
  return avail.length > 0 && avail.every(i => cart.selectedIds.includes(i.id))
}
function selectShop(group, on) {
  group.items.filter(i => i.available).forEach(i => cart.toggle(i.id, on))
}
function selectAll(on) {
  cart.setSelected(on ? selectable.value.map(i => i.id) : [])
}

async function changeQty(item, qty) {
  error.value = ''
  if (!Number.isInteger(qty) || qty < 1) {
    // going below 1 asks to remove the item, like Shopee
    if (qty < 1) await onRemove(item)
    return
  }
  const clamped = Math.min(qty, item.availableStock)
  if (clamped !== qty) error.value = `Rất tiếc, bạn chỉ có thể mua tối đa ${item.availableStock} sản phẩm này.`
  busyId.value = item.id
  try {
    await cart.updateQuantity(item.id, clamped)
  } catch (e) {
    error.value = extractError(e)
    await cart.fetchCart()
  } finally {
    busyId.value = null
  }
}

async function onRemove(item) {
  if (!confirm(`Bạn chắc chắn muốn bỏ "${item.productName}" khỏi giỏ hàng?`)) return
  await cart.remove(item.id)
}

async function onRemoveSelected() {
  if (!confirm(`Bỏ ${cart.selectedItems.length} sản phẩm đã chọn khỏi giỏ hàng?`)) return
  await cart.removeMany(cart.selectedItems.map(i => i.id))
}

async function onRemoveUnavailable() {
  await cart.removeMany(unavailableIds.value)
}

function goCheckout() {
  router.push({ name: 'checkout', query: { items: cart.selectedItems.map(i => i.id).join(',') } })
}

onMounted(async () => {
  await cart.fetchCart()
  // "Mua ngay" (one row) and "Mua lai" (several) land here with ?select=<cartItemId,...>
  String(route.query.select || '').split(',').map(Number).filter(Boolean)
    .forEach(id => { if (cart.items.some(i => i.id === id && i.available)) cart.toggle(id, true) })
  loaded.value = true
})
</script>

<style scoped>
.page { padding-top: 20px; padding-bottom: 40px; }
.grid-row { display: grid; grid-template-columns: minmax(0, 46%) 1fr 1fr 1fr 90px; align-items: center; gap: 8px; }
.center { text-align: center; }
.check-cell { display: flex; align-items: center; gap: 14px; }
.check-cell input { width: 18px; height: 18px; margin: 0; accent-color: var(--color-primary); flex-shrink: 0; }
.cart-head { background: white; padding: 16px 20px; border-radius: 3px; color: var(--color-text-muted); font-size: 14px; margin-bottom: 12px; }

.shop-card { background: white; border-radius: 3px; margin-bottom: 14px; }
.shop-header { padding: 14px 20px; border-bottom: 1px solid var(--color-border); }
.shop-badge { background: var(--color-primary); color: white; font-size: 11px; padding: 1px 5px; border-radius: 2px; }
.shop-name { font-weight: 500; }
.shop-name:hover { color: var(--color-primary); }

.item-row { padding: 18px 20px; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
.item-row:last-child { border-bottom: none; }
.product-cell { flex-wrap: wrap; }
.product-link { display: flex; gap: 10px; flex: 1; min-width: 0; }
.thumb { width: 80px; height: 80px; object-fit: cover; border: 1px solid var(--color-border); flex-shrink: 0; }
.product-text { min-width: 0; }
.name { line-height: 1.4; max-height: 40px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.product-link:hover .name { color: var(--color-primary); }
.freeship-tag { display: inline-block; margin-top: 6px; color: #00bfa5; border: 1px solid #00bfa5; font-size: 10px; padding: 0 4px; }
.variant { width: 100%; padding-left: 122px; font-size: 13px; color: var(--color-text-muted); margin-top: -18px; }
.price-cell { display: flex; flex-direction: column; gap: 2px; }
.qty-cell { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stock-hint { font-size: 12px; color: var(--color-primary); }
.line-total { color: var(--color-primary); }
.link-btn { background: none; border: none; cursor: pointer; font-size: 14px; color: var(--color-text); padding: 0; }
.link-btn:hover:not(:disabled) { color: var(--color-primary); }
.link-btn:disabled { color: #bbb; cursor: default; }
.link-btn.primary { color: var(--color-primary); }
.unavailable { opacity: 0.55; }
.unavailable-label { color: #d33; font-size: 13px; }

.checkout-bar {
  position: sticky; bottom: 0; z-index: 10;
  display: flex; align-items: center; gap: 22px; flex-wrap: wrap;
  background: white; padding: 16px 20px; margin-top: 12px;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.06); border-radius: 3px; font-size: 15px;
}
.summary { margin-left: auto; text-align: right; }
.total { color: var(--color-primary); font-size: 24px; font-weight: 500; margin-left: 4px; }
.saving { font-size: 13px; }
.saving span { color: var(--color-primary); }
.buy { min-width: 200px; }
.error { color: #d33; text-align: center; margin-top: 10px; }

.empty { text-align: center; padding: 80px 0; color: var(--color-text-muted); }
.empty svg { color: #ccc; }

@media (max-width: 760px) {
  .grid-row { grid-template-columns: 1fr; gap: 10px; }
  .cart-head span:not(:first-child), .cart-head .center { display: none; }
  .center { text-align: left; }
  .qty-cell { align-items: flex-start; }
  .variant { padding-left: 32px; margin-top: 0; }
  .summary { margin-left: 0; text-align: left; width: 100%; }
  .buy { width: 100%; }
}
</style>
