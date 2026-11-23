<template>
  <!-- "Tiep tuc voi Google / Facebook / GitHub": only the providers the admin has set up -->
  <div v-if="providers.length" class="social">
    <div class="or-divider"><span>HOẶC</span></div>
    <div class="buttons" :class="{ row: providers.length > 1 }">
      <a v-for="p in providers" :key="p.id" class="social-btn" :href="socialLoginUrl(p.id, startBase)" @click="remember">
        <SocialIcon :provider="p.id" />
        <span>{{ providers.length > 1 ? p.name : `${verb} với ${p.name}` }}</span>
      </a>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { socialProviders, socialLoginUrl } from '../../api/auth'
import SocialIcon from './SocialIcon.vue'

const props = defineProps({
  verb: { type: String, default: 'Đăng nhập' },
  // where OAuth2RedirectView should send the user after the sign-in
  redirect: { type: String, default: null }
})

const providers = ref([])
const startBase = ref(null)

onMounted(async () => {
  try {
    const { data } = await socialProviders()
    providers.value = data.providers || []
    startBase.value = data.startBase || null
  } catch {
    providers.value = []
  }
})

function remember() {
  // a sign-in, not a "Lien ket tai khoan" started earlier and abandoned
  sessionStorage.removeItem('oauth2_link_return')
  if (props.redirect) sessionStorage.setItem('oauth2_redirect', props.redirect)
  else sessionStorage.removeItem('oauth2_redirect')
}
</script>

<style scoped>
.buttons { display: flex; flex-direction: column; gap: 10px; }
.buttons.row { flex-direction: row; }
.buttons.row .social-btn { flex: 1; min-width: 0; }
.social-btn {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  height: 42px; padding: 0 8px;
  border: 1px solid rgba(0, 0, 0, 0.26);
  border-radius: 2px;
  font-size: 14px; color: var(--color-text);
  transition: background 0.15s;
  white-space: nowrap; overflow: hidden;
}
.social-btn:hover { background: rgba(0, 0, 0, 0.03); }
</style>
