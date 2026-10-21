import { ref, computed } from 'vue'
import { adminMe } from '../api/admin'

// The signed-in admin and their permissions, loaded once by AdminLayout and shared by the admin pages.
const me = ref(null)
let loading = null

export function useAdminMe() {
  async function load(force = false) {
    if (me.value && !force) return me.value
    if (!loading || force) {
      loading = adminMe().then(({ data }) => { me.value = data; return data }).finally(() => { loading = null })
    }
    return loading
  }
  const can = (perm) => !!me.value && (me.value.superAdmin || me.value.permissions.includes(perm))
  const permissionLabel = computed(() => Object.fromEntries((me.value?.allPermissions || []).map(p => [p.key, p.label])))
  return { me, load, can, permissionLabel }
}

export function resetAdminMe() {
  me.value = null
}
