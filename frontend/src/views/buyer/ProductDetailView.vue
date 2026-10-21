<template>
  <div>
    <AppHeader />
    <div v-if="notFound" class="container">
      <div class="content-block not-found">
        <p>Sản phẩm không tồn tại hoặc đã ngừng bán.</p>
        <router-link to="/" class="btn">Về trang chủ</router-link>
      </div>
    </div>

    <div v-else-if="product" class="container page">
      <!-- breadcrumb -->
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <router-link to="/">Shoppi</router-link>
        <template v-for="c in categoryPath" :key="c.id">
          <span class="sep">›</span>
          <router-link :to="{ name: 'category', params: { id: c.id } }">{{ c.name }}</router-link>
        </template>
        <span class="sep">›</span>
        <span class="current">{{ product.name }}</span>
      </nav>

      <div class="detail-card">
        <!-- ===== gallery: video first (if any), then images ===== -->
        <div class="gallery">
          <div class="main-media">
            <video v-if="current && current.type === 'video'" :src="current.url" controls playsinline></video>
            <img v-else :src="variantImage || (current ? current.url : placeholder)" :alt="product.name" />
            <span v-if="product.discountPercent" class="discount-badge">-{{ product.discountPercent }}%</span>
          </div>
          <div v-if="media.length > 1" class="thumbs">
            <button
              v-for="(m, i) in media"
              :key="m.url"
              type="button"
              class="thumb"
              :class="{ active: i === mediaIndex }"
              :aria-label="m.type === 'video' ? 'Xem video' : `Ảnh ${i + 1}`"
              @mouseenter="mediaIndex = i; variantImage = null"
              @click="mediaIndex = i; variantImage = null"
            >
              <video v-if="m.type === 'video'" :src="m.url" muted preload="metadata"></video>
              <img v-else :src="m.url" alt="" loading="lazy" />
              <span v-if="m.type === 'video'" class="play">▶</span>
            </button>
          </div>
        </div>

        <!-- ===== info ===== -->
        <div class="info">
          <h1 class="p-name">
            <span v-if="product.flashSale" class="tag-flash">⚡ Flash Sale</span>
            {{ product.name }}
          </h1>

          <div class="rating-row">
            <template v-if="product.ratingCount">
              <a href="#reviews" class="rating-link">
                <span class="rating-num">{{ product.ratingAvg.toFixed(1) }}</span>
                <StarRating :value="product.ratingAvg" :size="14" />
              </a>
              <span class="divider"></span>
              <a href="#reviews"><span class="num">{{ formatCount(product.ratingCount) }}</span> <span class="text-muted">Đánh Giá</span></a>
            </template>
            <span v-else class="text-muted">Chưa có đánh giá</span>
            <span class="divider"></span>
            <span><span class="num">{{ formatCount(product.soldCount) }}</span> <span class="text-muted">Đã Bán</span></span>
            <ReportProductButton v-if="!isOwnProduct && !auth.isAdmin" :product-id="product.id" class="report" />
          </div>

          <div v-if="product.flashSale" class="flash-bar">
            <span class="flash-label">⚡ FLASH SALE</span>
            <span class="flash-ends">KẾT THÚC TRONG
              <span v-for="(p, i) in countdown" :key="i" class="digit">{{ p }}</span>
            </span>
          </div>
          <div v-if="product.flashSale && (product.flashSaleStock || product.flashSaleMaxPerBuyer)" class="flash-quota">
            <template v-if="product.flashSaleStock">
              <div class="quota-bar"><span :style="{ width: Math.min(100, (product.flashSaleSold / product.flashSaleStock) * 100) + '%' }"></span></div>
              <span>Đã bán {{ product.flashSaleSold }}/{{ product.flashSaleStock }} suất giá sốc</span>
            </template>
            <span v-if="product.flashSaleMaxPerBuyer" class="quota-limit">Mỗi khách tối đa {{ product.flashSaleMaxPerBuyer }} sản phẩm</span>
          </div>

          <div class="price-box">
            <span v-if="displayOriginal" class="old-price">{{ formatVnd(displayOriginal) }}</span>
            <span class="price-big">{{ formatVnd(displayPrice) }}</span>
            <span v-if="product.discountPercent" class="off">{{ product.discountPercent }}% GIẢM</span>
          </div>

          <ShopVoucherStrip :shop-id="product.shopId" :product-id="product.id" label="Mã Giảm Giá" class="voucher-strip" />

          <dl class="rows">
            <dt>Vận Chuyển</dt>
            <dd>
              <div v-if="product.freeShipping" class="freeship">
                <span class="freeship-tag">Freeship</span> Miễn phí vận chuyển
              </div>
              <div v-else>Phí vận chuyển <strong>{{ formatVnd(20000) }}</strong></div>
              <div v-if="product.province" class="text-muted small">Gửi từ {{ product.province }}</div>
              <div v-if="shopInfo?.freeShipMinOrder" class="small freeship-shop">Shop miễn phí vận chuyển cho đơn từ {{ formatVnd(shopInfo.freeShipMinOrder) }}</div>
            </dd>

            <dt>Đổi Trả</dt>
            <dd>
              <template v-if="product.returnDays > 0">
                <span class="shield">✓</span> Đổi trả miễn phí trong <strong>{{ product.returnDays }} ngày</strong>
              </template>
              <span v-else class="text-muted">Sản phẩm không hỗ trợ đổi trả</span>
            </dd>

            <!-- Shopee-style groups: pick a colour, then a size -->
            <template v-if="tierGroups">
              <template v-for="(g, t) in tierGroups" :key="g.name">
                <dt>{{ g.name }}</dt>
                <dd>
                  <div class="variants" role="radiogroup" :aria-label="g.name">
                    <button
                      v-for="val in g.values"
                      :key="val.value"
                      type="button"
                      role="radio"
                      class="variant"
                      :class="{ selected: picked[t] === val.value }"
                      :aria-checked="picked[t] === val.value"
                      :disabled="!isAvailable(t, val.value)"
                      :title="isAvailable(t, val.value) ? '' : 'Hết hàng'"
                      @click="pick(t, val.value)"
                    ><img v-if="val.imageUrl" :src="val.imageUrl" alt="" class="opt-thumb" />{{ val.value }}</button>
                  </div>
                </dd>
              </template>
            </template>
            <template v-else-if="product.variants.length">
              <dt>Phân Loại</dt>
              <dd>
                <div class="variants" role="radiogroup" aria-label="Phân loại">
                  <button
                    v-for="v in product.variants"
                    :key="v.id"
                    type="button"
                    role="radio"
                    class="variant"
                    :class="{ selected: selectedVariantId === v.id }"
                    :aria-checked="selectedVariantId === v.id"
                    :disabled="v.stock <= 0"
                    :title="v.stock <= 0 ? 'Hết hàng' : ''"
                    @click="selectVariant(v)"
                  >{{ v.name }}</button>
                </div>
              </dd>
            </template>

            <dt>Số Lượng</dt>
            <dd class="qty-row">
              <div class="stepper">
                <button type="button" aria-label="Giảm" :disabled="quantity <= 1" @click="quantity--">−</button>
                <input v-model.number="quantity" type="number" min="1" :max="availableStock" aria-label="Số lượng" @blur="clampQty" />
                <button type="button" aria-label="Tăng" :disabled="quantity >= availableStock" @click="quantity++">+</button>
              </div>
              <span class="text-muted small">{{ availableStock > 0 ? `${availableStock} sản phẩm có sẵn` : 'Hết hàng' }}</span>
            </dd>
          </dl>

          <p v-if="actionError" class="action-error">{{ actionError }}</p>

          <div v-if="!auth.isLoggedIn || auth.isBuyer" class="action-row">
            <button class="btn secondary lg add-cart" :disabled="busy || availableStock <= 0" @click="onAddToCart">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13 5.4 5M7 13l-2.3 2.3c-.4.4-.1 1.2.5 1.2H17M17 17a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM9 19a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
              Thêm Vào Giỏ Hàng
            </button>
            <button class="btn lg" :disabled="busy || availableStock <= 0" @click="onBuyNow">Mua Ngay</button>
            <button type="button" class="btn lg chat-btn" @click="openChat">💬 Chat ngay</button>
          </div>
          <p v-else class="hint text-muted">Đăng nhập bằng tài khoản người mua để đặt mua sản phẩm này.</p>
        </div>
      </div>

      <!-- ===== shop ===== -->
      <div class="content-block shop-row">
        <div class="shop-avatar">
          <img v-if="product.shopLogoUrl" :src="product.shopLogoUrl" alt="" />
          <span v-else>{{ (product.shopName || '?').charAt(0).toUpperCase() }}</span>
        </div>
        <div class="shop-meta">
          <router-link :to="{ name: 'shop', params: { id: product.shopId } }" class="shop-name">
            {{ product.shopName }}
            <span v-if="shopInfo?.verified" class="verified-badge" title="Shop đã được Shoppi xác minh giấy tờ">✓ Đã xác minh</span>
          </router-link>
          <div class="text-faint small">
            <span v-if="product.shopRating">★ {{ product.shopRating.toFixed(1) }} đánh giá shop ({{ product.shopRatingCount }})</span>
            <span v-if="product.shopRating && product.province"> · </span>
            <span v-if="product.province">{{ product.province }}</span>
          </div>
          <div v-if="shopInfo?.chatResponseRate != null" class="text-faint small">
            Phản hồi chat <strong class="resp">{{ shopInfo.chatResponseRate.toLocaleString('vi-VN') }}%</strong><template v-if="shopInfo.chatResponseTime"> · {{ shopInfo.chatResponseTime }}</template>
          </div>
        </div>
        <!-- guests are sent to login and come back to the chat (route guard) -->
        <button v-if="!auth.isSeller && !auth.isAdmin" type="button" class="btn sm chat-btn" @click="openChat">💬 Chat ngay</button>
        <router-link :to="{ name: 'shop', params: { id: product.shopId } }" class="btn secondary sm">Xem Shop</router-link>
      </div>

      <!-- ===== combo deals ("Mua 2 giam 5%") ===== -->
      <section v-for="c in combos" :key="c.id" class="content-block combo-block">
        <div class="combo-head">
          <span class="combo-tag">Combo khuyến mãi</span>
          <strong>{{ c.name }}</strong>
          <span v-for="t in c.tiers" :key="t.minQuantity" class="combo-tier">Mua {{ t.minQuantity }} giảm {{ t.discountPercent }}%</span>
          <span v-if="c.status === 'UPCOMING'" class="text-faint small">· Bắt đầu {{ new Date(c.startAt).toLocaleString('vi-VN') }}</span>
        </div>
        <p class="small text-muted">Mua lẫn các sản phẩm dưới đây của shop — giảm giá tự động áp dụng khi thanh toán.</p>
        <div class="combo-products">
          <ProductCard v-for="cp in c.products" :key="cp.id" :product="cp" />
        </div>
      </section>

      <!-- ===== details, description, return policy ===== -->
      <section class="content-block">
        <h2 class="block-title">CHI TIẾT SẢN PHẨM</h2>
        <dl class="spec">
          <template v-if="categoryPath.length">
            <dt>Danh Mục</dt>
            <dd>
              <template v-for="(c, i) in categoryPath" :key="c.id">
                <span v-if="i" class="sep"> › </span>
                <router-link :to="{ name: 'category', params: { id: c.id } }" class="link">{{ c.name }}</router-link>
              </template>
            </dd>
          </template>
          <dt>Kho</dt><dd>{{ product.stock }}</dd>
          <template v-if="product.province"><dt>Gửi Từ</dt><dd>{{ product.province }}</dd></template>
        </dl>

        <h2 class="block-title">MÔ TẢ SẢN PHẨM</h2>
        <p class="description">{{ product.description || 'Chưa có mô tả cho sản phẩm này.' }}</p>

        <h2 class="block-title">CHÍNH SÁCH ĐỔI TRẢ</h2>
        <ul v-if="product.returnDays > 0" class="policy">
          <li>Được đổi trả trong <strong>{{ product.returnDays }} ngày</strong> kể từ khi nhận hàng.</li>
          <!-- the shop's own conditions (shop policy) replace the generic one -->
          <li v-if="shopInfo?.returnConditions" class="shop-conditions">Điều kiện của shop: {{ shopInfo.returnConditions }}</li>
          <li v-else>Sản phẩm còn nguyên tem, nhãn mác, chưa qua sử dụng và đầy đủ phụ kiện, quà tặng kèm (nếu có).</li>
          <li>Áp dụng khi sản phẩm bị lỗi, hư hỏng, giao sai mẫu/size/màu hoặc không đúng mô tả.</li>
          <li>Người mua gửi yêu cầu trả hàng trong mục "Đơn hàng của tôi"; tiền được hoàn sau khi Người Bán xác nhận.</li>
        </ul>
        <p v-else class="description">Sản phẩm này không áp dụng đổi trả, trừ trường hợp giao sai hoặc hàng lỗi do nhà sản xuất.</p>
      </section>

      <div id="reviews"></div>
      <ProductReviews :product-id="product.id" />

      <ProductQuestions :product-id="product.id" :is-own-product="isOwnProduct" />

      <section v-if="similar.length" class="content-block">
        <h2 class="block-title">SẢN PHẨM TƯƠNG TỰ</h2>
        <div class="grid-products">
          <ProductCard v-for="p in similar" :key="p.id" :product="p" />
        </div>
      </section>
    </div>

    <!-- chat with the shop right here, the product attached -->
    <ProductChatPopup v-if="chatOpen && product" :key="product.id" :product="product" :shop-name="product.shopName" @close="chatOpen = false" />

    <transition name="fade">
      <div v-if="toast" class="toast" role="status">
        <span class="toast-icon">✓</span>
        {{ toast }}
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../../components/AppHeader.vue'
import ProductCard from '../../components/ProductCard.vue'
import StarRating from '../../components/StarRating.vue'
import ProductReviews from '../../components/product/ProductReviews.vue'
import ReportProductButton from '../../components/product/ReportProductButton.vue'
import ProductQuestions from '../../components/product/ProductQuestions.vue'
import { getProduct, similarProducts, getCategory } from '../../api/products'
import { getMyShop, publicShop } from '../../api/shop'
import { productCombos } from '../../api/marketing'
import ShopVoucherStrip from '../../components/ShopVoucherStrip.vue'
import ProductChatPopup from '../../components/chat/ProductChatPopup.vue'
import { useAuthStore } from '../../stores/auth'
import { useCartStore } from '../../stores/cart'
import { useCountdown } from '../../composables/useCountdown'
import { extractError } from '../../utils/apiError'
import { formatVnd, formatCount } from '../../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const cart = useCartStore()

const product = ref(null)
const notFound = ref(false)
const similar = ref([])
const categoryPath = ref([])
const myShopId = ref(null)
const shopInfo = ref(null) // verified badge + shop policies
const combos = ref([])
const quantity = ref(1)
const selectedVariantId = ref(null)
const mediaIndex = ref(0)
const busy = ref(false)
const actionError = ref('')
const toast = ref('')
const placeholder = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="%23f0f0f0"/></svg>'

const { parts: countdown } = useCountdown(() => product.value?.flashSale ? product.value.flashSaleEnd : null)

const media = computed(() => {
  const p = product.value
  if (!p) return []
  const list = (p.imageUrls.length ? p.imageUrls : p.thumbnailUrl ? [p.thumbnailUrl] : []).map(url => ({ type: 'image', url }))
  return p.videoUrl ? [{ type: 'video', url: p.videoUrl }, ...list] : list
})
const current = computed(() => media.value[mediaIndex.value])

// ---- variation groups (tier1Name / tier2Name) ----
const picked = ref([null, null])
const variantImage = ref(null) // picture of the picked colour, shown instead of the gallery image
const tierGroups = computed(() => {
  const p = product.value
  if (!p?.tier1Name || !p.variants.length) return null
  const groups = [{ name: p.tier1Name, key: 'option1' }, p.tier2Name ? { name: p.tier2Name, key: 'option2' } : null].filter(Boolean)
  return groups.map(g => {
    const values = []
    for (const v of p.variants) {
      const val = v[g.key]
      if (!values.some(x => x.value === val)) values.push({ value: val, imageUrl: g.key === 'option1' ? v.imageUrl : null })
    }
    return { ...g, values }
  })
})
// a value can be picked if some in-stock variant has it (together with what is picked in the other group)
function isAvailable(t, value) {
  const groups = tierGroups.value
  return product.value.variants.some(v => v.stock > 0 && v[groups[t].key] === value
    && groups.every((g, i) => i === t || picked.value[i] == null || v[g.key] === picked.value[i]))
}
function pick(t, value) {
  const next = [...picked.value]
  next[t] = next[t] === value ? null : value
  // drop a value of the other group that is now impossible
  tierGroups.value.forEach((g, i) => {
    if (i !== t && next[t] != null && next[i] != null && !product.value.variants.some(v => v[g.key] === next[i] && v[tierGroups.value[t].key] === next[t])) next[i] = null
  })
  picked.value = next
  const groups = tierGroups.value
  const match = groups.every((_, i) => next[i] != null)
    ? product.value.variants.find(v => groups.every((g, i) => v[g.key] === next[i]))
    : null
  selectedVariantId.value = match ? match.id : null
  if (t === 0) variantImage.value = next[0] != null ? groups[0].values.find(x => x.value === next[0])?.imageUrl || null : null
}

const selectedVariant = computed(() => product.value?.variants.find(v => v.id === selectedVariantId.value) || null)
// before a variant is picked show the cheapest-to-dearest range, like Shopee
const priceRange = computed(() => {
  const vs = product.value?.variants || []
  if (!vs.length) return null
  const prices = vs.map(v => v.price)
  return { min: Math.min(...prices), max: Math.max(...prices) }
})
const displayPrice = computed(() => selectedVariant.value?.price ?? priceRange.value?.min ?? product.value.price)
const displayOriginal = computed(() => {
  const p = product.value
  if (!p.originalPrice) return null
  // keep the same discount on top of the variant's extra price
  const extra = selectedVariant.value?.extraPrice || 0
  return Number(p.originalPrice) + Number(extra)
})
const availableStock = computed(() => selectedVariant.value ? selectedVariant.value.stock : product.value.stock)
const isOwnProduct = computed(() => auth.isSeller && myShopId.value != null && myShopId.value === product.value?.shopId)

function selectVariant(v) {
  selectedVariantId.value = selectedVariantId.value === v.id ? null : v.id
  actionError.value = ''
  clampQty()
}
function clampQty() {
  const max = Math.max(1, availableStock.value)
  if (!Number.isInteger(quantity.value) || quantity.value < 1) quantity.value = 1
  if (quantity.value > max) quantity.value = max
}

const chatOpen = ref(false)
// guests sign in first and come back to this product
function openChat() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  chatOpen.value = true
}

async function addToCart() {
  if (!auth.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return null
  }
  if (product.value.variants.length && !selectedVariantId.value) {
    actionError.value = 'Vui lòng chọn Phân loại hàng'
    return null
  }
  actionError.value = ''
  busy.value = true
  try {
    return await cart.add(product.value.id, quantity.value, selectedVariantId.value) // the cart row
  } catch (e) {
    actionError.value = extractError(e)
    return null
  } finally {
    busy.value = false
  }
}

async function onAddToCart() {
  if (await addToCart()) {
    toast.value = 'Sản phẩm đã được thêm vào Giỏ hàng'
    setTimeout(() => { toast.value = '' }, 1800)
  }
}

// like Shopee: go to the cart with this product already ticked
async function onBuyNow() {
  const row = await addToCart()
  if (row) router.push({ name: 'cart', query: { select: row.id } })
}

async function load(id) {
  notFound.value = false
  product.value = null
  selectedVariantId.value = null
  picked.value = [null, null]
  variantImage.value = null
  mediaIndex.value = 0
  quantity.value = 1
  actionError.value = ''
  try {
    const { data } = await getProduct(id)
    product.value = data
  } catch {
    notFound.value = true
    return
  }
  window.scrollTo({ top: 0 })

  similarProducts(id).then(({ data }) => { similar.value = data }).catch(() => { similar.value = [] })
  shopInfo.value = null
  publicShop(product.value.shopId).then(({ data }) => { shopInfo.value = data }).catch(() => {})
  combos.value = []
  productCombos(id).then(({ data }) => { combos.value = data }).catch(() => {})
  categoryPath.value = []
  if (product.value.categoryId) {
    getCategory(product.value.categoryId).then(({ data }) => { categoryPath.value = data.path }).catch(() => {})
  }
  if (auth.isSeller && myShopId.value == null) {
    getMyShop().then(({ data }) => { myShopId.value = data.id }).catch(() => {})
  }
}

watch(() => route.params.id, (id) => { if (id) load(id) }, { immediate: true })
</script>

<style scoped>
.page { padding-top: 16px; padding-bottom: 40px; }
.breadcrumb { display: flex; align-items: center; gap: 6px; font-size: 14px; margin-bottom: 12px; flex-wrap: wrap; }
.breadcrumb a { color: #05a; }
.breadcrumb .sep { color: var(--color-text-faint); }
.breadcrumb .current { color: var(--color-text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 420px; }

.detail-card { display: grid; grid-template-columns: 450px minmax(0, 1fr); gap: 32px; background: white; padding: 16px; border-radius: 4px; }

.main-media { position: relative; aspect-ratio: 1; background: #f5f5f5; }
.main-media img, .main-media video { width: 100%; height: 100%; object-fit: contain; background: #000; }
.main-media img { background: #f5f5f5; }
.discount-badge { position: absolute; top: 0; right: 0; background: #ffe97a; color: var(--color-primary); font-weight: 700; padding: 4px 8px; }
.thumbs { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin-top: 8px; }
.thumb { position: relative; aspect-ratio: 1; padding: 0; border: 2px solid transparent; background: #f5f5f5; cursor: pointer; overflow: hidden; }
.thumb.active { border-color: var(--color-primary); }
.thumb img, .thumb video { width: 100%; height: 100%; object-fit: cover; }
.play { position: absolute; inset: 0; margin: auto; width: 28px; height: 28px; border-radius: 50%; background: rgba(0, 0, 0, 0.55); color: white; font-size: 11px; display: flex; align-items: center; justify-content: center; }

.p-name { font-size: 20px; font-weight: 500; line-height: 1.4; margin: 0 0 10px; }
.tag-flash { background: var(--color-primary); color: white; font-size: 12px; padding: 2px 6px; border-radius: 2px; vertical-align: 3px; }
.rating-row { display: flex; align-items: center; gap: 14px; font-size: 14px; margin-bottom: 14px; flex-wrap: wrap; }
.rating-link { display: flex; align-items: center; gap: 6px; }
.rating-num { color: var(--color-primary); text-decoration: underline; font-size: 16px; }
.num { text-decoration: underline; font-size: 16px; }
.divider { width: 1px; height: 18px; background: #ddd; }

.flash-bar { display: flex; justify-content: space-between; align-items: center; background: linear-gradient(90deg, #f53d2d, #f63); color: white; padding: 8px 16px; flex-wrap: wrap; gap: 8px; }
.flash-label { font-weight: 800; font-style: italic; font-size: 17px; }
.flash-ends { font-size: 13px; display: flex; align-items: center; gap: 4px; }
.flash-quota { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 8px 16px; background: #fff3f0; font-size: 13px; color: var(--color-primary); }
.quota-bar { width: 160px; height: 8px; background: #ffd5cb; border-radius: 999px; overflow: hidden; }
.quota-bar span { display: block; height: 100%; background: linear-gradient(90deg, #f53d2d, #f63); }
.quota-limit { color: var(--color-text-muted); }
.voucher-strip { margin: 14px 0 4px; }
.combo-block { border-left: 3px solid var(--color-primary); }
.combo-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.combo-tag { background: var(--color-primary); color: white; font-size: 12px; padding: 2px 8px; border-radius: 2px; }
.combo-tier { border: 1px dashed var(--color-primary); color: var(--color-primary); font-size: 12px; padding: 2px 8px; border-radius: 2px; background: var(--color-primary-light); }
.combo-products { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; }
.digit { background: #222; padding: 2px 5px; border-radius: 2px; font-weight: 700; font-variant-numeric: tabular-nums; }

.price-box { display: flex; align-items: center; gap: 14px; background: #fafafa; padding: 16px 20px; flex-wrap: wrap; }
.old-price { color: var(--color-text-faint); text-decoration: line-through; font-size: 16px; }
.price-big { color: var(--color-primary); font-size: 30px; font-weight: 500; }
.off { background: var(--color-primary); color: white; font-size: 12px; font-weight: 700; padding: 2px 6px; border-radius: 2px; }

.rows { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 22px 12px; margin: 24px 0 8px; padding: 0 20px; font-size: 14px; }
.rows dt { color: var(--color-text-muted); padding-top: 4px; }
.rows dd { margin: 0; }
.small { font-size: 13px; margin-top: 4px; }
.freeship-tag { color: #00bfa5; border: 1px solid #00bfa5; font-size: 11px; padding: 0 4px; margin-right: 4px; }
.shield { color: var(--color-primary); font-weight: 700; margin-right: 4px; }
.variants { display: flex; flex-wrap: wrap; gap: 8px; }
.variant {
  min-width: 80px; padding: 7px 14px;
  background: white; border: 1px solid rgba(0, 0, 0, 0.12); border-radius: 2px;
  cursor: pointer; font-size: 14px;
}
.variant:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
.variant.selected { border-color: var(--color-primary); color: var(--color-primary); box-shadow: inset 0 0 0 1px var(--color-primary); }
.variant:disabled { color: #bbb; background: #fafafa; cursor: not-allowed; text-decoration: line-through; }
.variant { display: inline-flex; align-items: center; gap: 6px; }
.opt-thumb { width: 24px; height: 24px; object-fit: cover; border-radius: 2px; }
.qty-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.stepper button:disabled { opacity: 0.4; cursor: not-allowed; }

.action-error { color: #d33; font-size: 14px; margin: 8px 20px 0; }
.action-row { display: flex; gap: 14px; margin: 24px 20px 0; flex-wrap: wrap; }
.add-cart { background: rgba(255, 87, 34, 0.1); }
.hint { margin: 24px 20px 0; }

.shop-row { display: flex; align-items: center; gap: 14px; }
.chat-btn { background: var(--color-primary-light); color: var(--color-primary); border: 1px solid var(--color-primary); }
.chat-btn:hover { background: #ffe1d9; }
.shop-avatar { width: 64px; height: 64px; border-radius: 50%; overflow: hidden; background: var(--color-primary-light); color: var(--color-primary); font-size: 26px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.shop-avatar img { width: 100%; height: 100%; object-fit: cover; }
.shop-meta { flex: 1; min-width: 0; }
.shop-name { font-weight: 600; font-size: 16px; display: inline-flex; align-items: center; gap: 8px; }
.shop-name:hover { color: var(--color-primary); }
.verified-badge { background: #2673dd; color: white; font-size: 11px; font-weight: 500; padding: 1px 6px; border-radius: 2px; }
.freeship-shop { color: var(--color-success); margin-top: 2px; }
.shop-conditions { white-space: pre-line; }

.spec { display: grid; grid-template-columns: 140px minmax(0, 1fr); gap: 14px; font-size: 14px; margin: 0 0 30px; padding: 0 16px; }
.spec dt { color: var(--color-text-muted); }
.spec dd { margin: 0; }
.spec .link { color: #05a; }
.spec .sep { color: var(--color-text-faint); }
.description { white-space: pre-line; line-height: 1.8; font-size: 14px; padding: 0 16px; margin: 0 0 30px; }
.policy { font-size: 14px; line-height: 1.8; margin: 0; padding: 0 16px 0 36px; }

.not-found { text-align: center; padding: 60px; margin-top: 16px; }

.toast { position: fixed; top: 45%; left: 50%; transform: translate(-50%, -50%); z-index: 1000; background: rgba(0, 0, 0, 0.75); color: white; padding: 28px 32px; border-radius: 4px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.toast-icon { width: 52px; height: 52px; border-radius: 50%; background: #00bfa5; display: flex; align-items: center; justify-content: center; font-size: 26px; }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 900px) {
  .detail-card { grid-template-columns: 1fr; gap: 16px; }
  .rows, .action-row { padding: 0; margin-left: 0; margin-right: 0; }
  .rows { grid-template-columns: 90px minmax(0, 1fr); }
}
.resp { color: var(--color-primary); font-weight: 500; }
.report { margin-left: auto; }
</style>
