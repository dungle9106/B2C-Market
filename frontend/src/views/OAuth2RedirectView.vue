<template>
  <div class="oauth-redirect">
    <div class="spinner"></div>
    <p>Đang hoàn tất đăng nhập Google...</p>
  </div>
</template>

<script setup>
// Landing page for the backend's redirect after Google OAuth2 login:
//   /oauth2/redirect#token=<our JWT>   or   /oauth2/redirect#error=<message>
// The token travels in the fragment (never sent to a server), and we clear
// it from the address bar right away with router.replace.
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  const params = new URLSearchParams(route.hash.replace(/^#/, ''))
  const token = params.get('token')
  const refresh = params.get('refresh')
  const challenge = params.get('challenge')
  const error = params.get('error')
  // the account uses two-step verification: finish on the login page
  if (challenge) {
    router.replace({ name: 'login', query: { challenge } })
    return
  }

  // set by AuthView before leaving for Google, so we can return the user
  // to the page they were trying to reach
  const redirect = sessionStorage.getItem('oauth2_redirect')
  sessionStorage.removeItem('oauth2_redirect')

  if (!token) {
    router.replace({ name: 'login', query: { error: error || 'Đăng nhập Google thất bại, vui lòng thử lại' } })
    return
  }

  try {
    await auth.loginWithToken(token, refresh)
    router.replace(redirect || (auth.isSeller ? '/seller' : auth.isAdmin ? '/admin' : '/'))
  } catch (e) {
    auth.logout(false)
    router.replace({ name: 'login', query: { error: 'Không lấy được thông tin tài khoản, vui lòng thử lại' } })
  }
})
</script>

<style scoped>
.oauth-redirect {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--color-text-muted);
}
.spinner {
  width: 36px; height: 36px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
