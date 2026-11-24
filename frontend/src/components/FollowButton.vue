<template>
  <!-- "+ Theo doi" / "Dang theo doi"; hidden for the shop owner and admins -->
  <button v-if="!auth.isSeller && !auth.isAdmin" type="button" class="btn sm follow" :class="[{ on: state.following }, variant]"
          :disabled="busy" :aria-pressed="state.following" @click="toggle">
    {{ state.following ? '✓ Đang theo dõi' : '+ Theo dõi' }}
  </button>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { shopFollowers, followShop, unfollowShop } from '../api/social'

// variant: 'light' on the dark shop banner, '' elsewhere
const props = defineProps({ shopId: { type: Number, required: true }, variant: { type: String, default: '' } })
const emit = defineEmits(['change'])
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const state = reactive({ count: 0, following: false, followerVouchers: 0 })
const busy = ref(false)

watch(() => props.shopId, async (id) => {
  try {
    Object.assign(state, (await shopFollowers(id)).data)
    emit('change', { ...state })
  } catch {
    // shown as not following
  }
}, { immediate: true })

async function toggle() {
  if (!auth.isLoggedIn) return router.push({ name: 'login', query: { redirect: route.fullPath } })
  if (state.following && !confirm('Bỏ theo dõi shop này? Bạn sẽ không dùng được voucher dành cho người theo dõi.')) return
  busy.value = true
  try {
    Object.assign(state, (await (state.following ? unfollowShop : followShop)(props.shopId)).data)
    emit('change', { ...state })
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.follow { min-width: 120px; }
.follow.on { background: white; color: var(--color-text); border: 1px solid var(--color-border); }
.follow.light:not(.on) { background: transparent; border: 1px solid rgba(255, 255, 255, .8); color: white; }
.follow.light.on { background: rgba(255, 255, 255, .15); color: white; border-color: rgba(255, 255, 255, .5); }
</style>
