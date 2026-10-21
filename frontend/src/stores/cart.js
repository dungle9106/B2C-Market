import { defineStore } from 'pinia'
import { getCart, addToCart, updateCartQuantity, removeFromCart, removeManyFromCart } from '../api/cart'

// which cart rows are ticked survives a page reload within the tab
const SELECTED_KEY = 'cart_selected'
function loadSelected() {
  try { return JSON.parse(sessionStorage.getItem(SELECTED_KEY)) || [] } catch { return [] }
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    selectedIds: loadSelected()
  }),

  getters: {
    count: (state) => state.items.reduce((sum, i) => sum + i.quantity, 0),

    // group items by shop -- checkout creates one order per shop
    groupedByShop: (state) => {
      const groups = {}
      for (const item of state.items) {
        if (!groups[item.shopId]) {
          groups[item.shopId] = { shopId: item.shopId, shopName: item.shopName, items: [] }
        }
        groups[item.shopId].items.push(item)
      }
      return Object.values(groups)
    },

    selectedItems: (state) => state.items.filter(i => i.available && state.selectedIds.includes(i.id))
  },

  actions: {
    async fetchCart() {
      const { data } = await getCart()
      this.items = data
      // forget selections of rows that disappeared or became unavailable
      this.setSelected(this.selectedIds.filter(id => data.some(i => i.id === id && i.available)))
    },

    // returns the cart row for the product/variant (new or merged)
    async add(productId, quantity, variantId = null) {
      const { data } = await addToCart({ productId, variantId, quantity })
      await this.fetchCart()
      return data
    },

    async updateQuantity(cartItemId, quantity) {
      await updateCartQuantity(cartItemId, quantity)
      await this.fetchCart()
    },

    async remove(cartItemId) {
      await removeFromCart(cartItemId)
      await this.fetchCart()
    },

    async removeMany(ids) {
      if (!ids.length) return
      await removeManyFromCart(ids)
      await this.fetchCart()
    },

    setSelected(ids) {
      this.selectedIds = [...new Set(ids)]
      try { sessionStorage.setItem(SELECTED_KEY, JSON.stringify(this.selectedIds)) } catch { /* ignore */ }
    },

    toggle(id, on) {
      const set = new Set(this.selectedIds)
      if (on ?? !set.has(id)) set.add(id); else set.delete(id)
      this.setSelected([...set])
    }
  }
})
