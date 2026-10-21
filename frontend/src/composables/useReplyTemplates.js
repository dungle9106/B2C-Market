import { ref } from 'vue'
import { replyTemplates } from '../api/customerCare'

// The shop's reply templates, loaded once per page load and shared by chat, Q&A and reviews.
const templates = ref(null)
let loading = null

export function useReplyTemplates() {
  async function load(force = false) {
    if (templates.value && !force) return templates.value
    if (!loading || force) {
      loading = replyTemplates()
        .then(({ data }) => { templates.value = data; return data })
        .finally(() => { loading = null })
    }
    return loading
  }
  return { templates, load }
}

// {ten_khach}, {ten_shop}, {ten_san_pham} -> the real names (same rule as the server)
export function fillPlaceholders(text, { customer, shop, product } = {}) {
  return (text || '')
    .replaceAll('{ten_khach}', customer?.trim() || 'bạn')
    .replaceAll('{ten_shop}', shop?.trim() || 'Shop')
    .replaceAll('{ten_san_pham}', product?.trim() || 'sản phẩm')
}

export const PLACEHOLDERS = [
  { key: '{ten_khach}', label: 'Tên khách' },
  { key: '{ten_shop}', label: 'Tên shop' },
  { key: '{ten_san_pham}', label: 'Tên sản phẩm' }
]
