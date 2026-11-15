<template>
  <router-view />
  <BottomTabBar ref="tabbar" />
  <BackToTop v-if="!inConsole" :lifted="hasTabbar" />
</template>

<script setup>
// Root component: everything (header, layout) is composed per-route via layouts,
// so different chrome can be used for buyer / seller / admin sections.
// Shared buyer chrome: the phone tab bar and the "back to top" button.
import { ref, computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import BottomTabBar from './components/BottomTabBar.vue'
import BackToTop from './components/BackToTop.vue'

const route = useRoute()
const tabbar = ref(null)
const hasTabbar = computed(() => !!tabbar.value?.visible)
const inConsole = computed(() => route.path.startsWith('/seller') || route.path.startsWith('/admin'))
// the body keeps room for the tab bar so the last row of the page is not hidden behind it
watchEffect(() => document.body.classList.toggle('has-tabbar', hasTabbar.value))
</script>
