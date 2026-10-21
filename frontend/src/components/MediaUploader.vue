<template>
  <div class="uploader">
    <div class="tiles">
      <div v-for="(url, i) in modelValue" :key="url" class="tile">
        <video v-if="kind === 'video'" :src="url" muted preload="metadata"></video>
        <img v-else :src="url" alt="" />
        <span v-if="coverLabel && i === 0" class="cover">{{ coverLabel }}</span>
        <button type="button" class="remove" aria-label="Xóa" @click="remove(i)">✕</button>
      </div>

      <label v-if="modelValue.length < max" class="tile add" :class="{ busy: uploading }">
        <input type="file" :accept="accept" :multiple="max - modelValue.length > 1" :disabled="uploading" @change="onFiles" />
        <svg v-if="!uploading" width="26" height="26" viewBox="0 0 24 24" fill="none">
          <path v-if="kind === 'video'" d="M3 6h12v12H3zM15 10l6-3v10l-6-3" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
          <path v-else d="M4 7h3l2-2h6l2 2h3v12H4zM12 10v6M9 13h6" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" />
        </svg>
        <span v-if="uploading" class="spinner"></span>
        <span class="add-text">{{ uploading ? 'Đang tải...' : `${addLabel} (${modelValue.length}/${max})` }}</span>
      </label>
    </div>

    <div v-if="allowUrl && modelValue.length < max" class="url-row">
      <input v-model.trim="urlDraft" type="url" placeholder="Hoặc dán đường dẫn https://..." @keydown.enter.prevent="addUrl" />
      <button type="button" class="btn ghost sm" :disabled="!urlDraft" @click="addUrl">Thêm</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { uploadFile } from '../api/uploads'
import { extractError } from '../utils/apiError'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },  // list of URLs
  kind: { type: String, default: 'image' },          // 'image' | 'video'
  max: { type: Number, default: 9 },
  allowUrl: { type: Boolean, default: false },
  coverLabel: { type: String, default: '' },        // e.g. "Ảnh bìa" on the first tile
  addLabel: { type: String, default: 'Thêm hình ảnh' }
})
const emit = defineEmits(['update:modelValue'])

const accept = computed(() => (props.kind === 'video' ? 'video/mp4,video/webm' : 'image/jpeg,image/png,image/webp,image/gif'))
const uploading = ref(false)
const error = ref('')
const urlDraft = ref('')

async function onFiles(e) {
  const files = [...e.target.files].slice(0, props.max - props.modelValue.length)
  e.target.value = '' // allow re-picking the same file
  if (!files.length) return
  error.value = ''
  uploading.value = true
  const added = []
  try {
    for (const f of files) {
      const { data } = await uploadFile(f)
      added.push(data.url)
    }
  } catch (err) {
    error.value = extractError(err)
  } finally {
    uploading.value = false
    if (added.length) emit('update:modelValue', [...props.modelValue, ...added])
  }
}

function addUrl() {
  if (!/^https?:\/\//.test(urlDraft.value)) {
    error.value = 'Đường dẫn phải bắt đầu bằng http:// hoặc https://'
    return
  }
  error.value = ''
  if (!props.modelValue.includes(urlDraft.value)) emit('update:modelValue', [...props.modelValue, urlDraft.value])
  urlDraft.value = ''
}

function remove(i) {
  emit('update:modelValue', props.modelValue.filter((_, idx) => idx !== i))
}
</script>

<style scoped>
.tiles { display: flex; flex-wrap: wrap; gap: 10px; }
.tile { position: relative; width: 84px; height: 84px; border: 1px solid var(--color-border); border-radius: 4px; overflow: hidden; background: #f7f7f7; }
.tile img, .tile video { width: 100%; height: 100%; object-fit: cover; }
.cover { position: absolute; left: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.55); color: white; font-size: 11px; text-align: center; padding: 2px 0; }
.remove { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border-radius: 50%; border: none; background: rgba(0, 0, 0, 0.55); color: white; font-size: 11px; cursor: pointer; line-height: 20px; padding: 0; }
.add { border: 1px dashed var(--color-primary); background: var(--color-primary-light); color: var(--color-primary); cursor: pointer; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; text-align: center; }
.add.busy { cursor: progress; opacity: 0.8; }
.add input { position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; margin: 0; }
.add-text { font-size: 11px; line-height: 1.2; padding: 0 4px; }
.spinner { width: 20px; height: 20px; border: 2px solid var(--color-primary-light); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.url-row { display: flex; gap: 8px; margin-top: 10px; max-width: 520px; }
.url-row input { margin: 0; }
.error { color: #d33; font-size: 13px; margin: 8px 0 0; }
</style>
