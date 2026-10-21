<template>
  <div>
    <div class="category-grid">
      <router-link
        v-for="c in categories"
        :key="c.id"
        :to="{ name: 'category', params: { id: c.id } }"
        class="category-item"
      >
        <span class="icon-circle">{{ iconFor(c.name) }}</span>
        <span>{{ c.name }}</span>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listCategories } from '../api/products'

const categories = ref([])

// small deterministic emoji-per-category so the icon grid doesn't look empty
// while the project doesn't have real category icon assets yet
const ICONS = ['🛍️', '📱', '🏠', '💄', '📚', '👗', '⚽', '🎮']
function iconFor(name) {
  let sum = 0
  for (const ch of name) sum += ch.charCodeAt(0)
  return ICONS[sum % ICONS.length]
}

onMounted(async () => {
  const { data } = await listCategories()
  categories.value = data
})
</script>
