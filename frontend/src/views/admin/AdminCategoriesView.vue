<template>
  <section class="sc-card">
    <div class="sc-card-head">
      <h2 class="sc-card-title">Danh Mục Ngành Hàng</h2>
      <span class="sc-card-sub">Danh mục chuẩn toàn sàn (2 cấp) dùng cho menu người mua và khi người bán đăng sản phẩm</span>
    </div>

    <form class="add-top" @submit.prevent="onCreate(null)">
      <input v-model.trim="newName[0]" placeholder="Tên danh mục cấp 1 mới" maxlength="60" />
      <button class="btn sm" type="submit" :disabled="!newName[0]">+ Thêm danh mục</button>
    </form>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="!tree" class="sc-empty">Đang tải...</div>
    <ul v-else class="tree">
      <li v-for="(c, i) in tree" :key="c.id" class="top">
        <div class="node" :class="{ hidden: !c.active }">
          <span class="order">
            <button type="button" :disabled="i === 0" title="Lên" @click="move(null, tree, i, -1)">▲</button>
            <button type="button" :disabled="i === tree.length - 1" title="Xuống" @click="move(null, tree, i, 1)">▼</button>
          </span>
          <template v-if="editing === c.id">
            <input v-model.trim="editName" class="edit" maxlength="60" @keydown.enter.prevent="onRename(c, null)" />
            <button type="button" class="link" @click="onRename(c, null)">Lưu</button>
            <button type="button" class="link muted" @click="editing = null">Hủy</button>
          </template>
          <template v-else>
            <strong class="name" @click="expanded[c.id] = !expanded[c.id]">{{ expanded[c.id] ? '▾' : '▸' }} {{ c.name }}</strong>
            <span class="count">{{ c.productCount }} sản phẩm · {{ c.children.length }} danh mục con</span>
            <span v-if="!c.active" class="tag">Đang ẩn</span>
            <span class="spacer"></span>
            <button type="button" class="link" @click="startEdit(c)">Đổi tên</button>
            <button type="button" class="link" @click="onToggle(c)">{{ c.active ? 'Ẩn' : 'Hiện' }}</button>
            <button type="button" class="link danger" @click="askDelete(c)">Xóa</button>
          </template>
        </div>

        <ul v-if="expanded[c.id]" class="children">
          <li v-for="(s, j) in c.children" :key="s.id">
            <div class="node sub" :class="{ hidden: !s.active || !c.active }">
              <span class="order">
                <button type="button" :disabled="j === 0" @click="move(c.id, c.children, j, -1)">▲</button>
                <button type="button" :disabled="j === c.children.length - 1" @click="move(c.id, c.children, j, 1)">▼</button>
              </span>
              <template v-if="editing === s.id">
                <input v-model.trim="editName" class="edit" maxlength="60" @keydown.enter.prevent="onRename(s, editParent)" />
                <select v-model="editParent" class="edit-parent" title="Chuyển sang danh mục cha khác">
                  <option v-for="t in tree" :key="t.id" :value="t.id">{{ t.name }}</option>
                </select>
                <button type="button" class="link" @click="onRename(s, editParent)">Lưu</button>
                <button type="button" class="link muted" @click="editing = null">Hủy</button>
              </template>
              <template v-else>
                <span class="name">{{ s.name }}</span>
                <span class="count">{{ s.productCount }} sản phẩm</span>
                <span v-if="!s.active" class="tag">Đang ẩn</span>
                <span class="spacer"></span>
                <button type="button" class="link" @click="startEdit(s, c.id)">Sửa / chuyển</button>
                <button type="button" class="link" @click="onToggle(s)">{{ s.active ? 'Ẩn' : 'Hiện' }}</button>
                <button type="button" class="link danger" @click="askDelete(s)">Xóa</button>
              </template>
            </div>
          </li>
          <li>
            <form class="add-sub" @submit.prevent="onCreate(c.id)">
              <input v-model.trim="newName[c.id]" :placeholder="`Thêm danh mục con cho ${c.name}`" maxlength="60" />
              <button class="btn sm secondary" type="submit" :disabled="!newName[c.id]">+ Thêm</button>
            </form>
          </li>
        </ul>
      </li>
    </ul>
    <p class="hint">Danh mục bị ẩn không hiện trên menu người mua và khi người bán chọn ngành hàng; sản phẩm đã đăng trong danh mục vẫn được bán bình thường.</p>

    <BaseModal v-if="deleting" title="Xóa danh mục" @close="deleting = null">
      <div class="del">
        <p>Xóa <strong>{{ deleting.name }}</strong>?</p>
        <p v-if="deleting.children?.length" class="error">Danh mục còn {{ deleting.children.length }} danh mục con, hãy xóa hoặc chuyển chúng trước.</p>
        <template v-else-if="deleting.ownProductCount > 0">
          <p>Danh mục đang có <strong>{{ deleting.ownProductCount }} sản phẩm</strong>. Chọn danh mục để chuyển các sản phẩm này sang:</p>
          <select v-model="moveTo">
            <option :value="null">-- Chọn danh mục --</option>
            <template v-for="t in tree" :key="t.id">
              <option v-if="t.id !== deleting.id" :value="t.id">{{ t.name }}</option>
              <option v-for="s in t.children.filter(x => x.id !== deleting.id)" :key="s.id" :value="s.id">&nbsp;&nbsp;└ {{ s.name }}</option>
            </template>
          </select>
        </template>
        <p v-if="deleteError" class="error">{{ deleteError }}</p>
        <div class="btns">
          <button type="button" class="btn ghost sm" @click="deleting = null">Hủy</button>
          <button type="button" class="btn sm danger" :disabled="!!deleting.children?.length || (deleting.ownProductCount > 0 && !moveTo)" @click="onDelete">Xóa danh mục</button>
        </div>
      </div>
    </BaseModal>
  </section>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import BaseModal from '../../components/BaseModal.vue'
import { adminCategories, createCategory, updateCategory, toggleCategory, reorderCategories, deleteCategory } from '../../api/admin'
import { extractError } from '../../utils/apiError'

const tree = ref(null)
const expanded = reactive({})
const newName = reactive({})
const editing = ref(null)
const editName = ref('')
const editParent = ref(null)
const error = ref('')
const deleting = ref(null)
const moveTo = ref(null)
const deleteError = ref('')

async function load() {
  try {
    tree.value = (await adminCategories()).data
  } catch (e) {
    error.value = extractError(e)
  }
}

async function run(fn) {
  error.value = ''
  try {
    await fn()
    await load()
    return true
  } catch (e) {
    error.value = extractError(e)
    return false
  }
}

async function onCreate(parentId) {
  const key = parentId || 0
  if (await run(() => createCategory(newName[key], parentId))) {
    newName[key] = ''
    if (parentId) expanded[parentId] = true
  }
}

function startEdit(c, parentId = null) {
  editing.value = c.id
  editName.value = c.name
  editParent.value = parentId
}

async function onRename(c, parentId) {
  if (await run(() => updateCategory(c.id, editName.value, parentId))) editing.value = null
}

const onToggle = (c) => run(() => toggleCategory(c.id))

// swap with the neighbour and save the new order of that level
async function move(parentId, list, i, dir) {
  const ids = list.map(x => x.id)
  ;[ids[i], ids[i + dir]] = [ids[i + dir], ids[i]]
  await run(() => reorderCategories(parentId, ids))
}

function askDelete(c) {
  deleting.value = c
  moveTo.value = null
  deleteError.value = ''
}

async function onDelete() {
  deleteError.value = ''
  try {
    await deleteCategory(deleting.value.id, moveTo.value)
    deleting.value = null
    await load()
  } catch (e) {
    deleteError.value = extractError(e)
  }
}

onMounted(load)
</script>

<style scoped>
.add-top, .add-sub { display: flex; gap: 8px; align-items: center; }
.add-top input, .add-sub input { margin: 0; max-width: 360px; }
.add-top { margin-bottom: 14px; }
.tree { list-style: none; margin: 0; padding: 0; border: 1px solid var(--color-border); border-radius: 6px; }
.top + .top { border-top: 1px solid var(--color-border); }
.node { display: flex; align-items: center; gap: 10px; padding: 10px 12px; font-size: 14px; }
.node.sub { padding-left: 46px; background: #fcfcfc; }
.node.hidden .name { color: var(--color-text-faint); text-decoration: line-through; }
.name { cursor: pointer; }
.count { font-size: 12px; color: var(--color-text-faint); }
.tag { font-size: 11px; background: #f2f2f2; color: #888; border-radius: 3px; padding: 1px 6px; }
.spacer { flex: 1; }
.order { display: flex; flex-direction: column; }
.order button { background: none; border: none; font-size: 9px; line-height: 1; color: #999; cursor: pointer; padding: 1px 3px; }
.order button:disabled { opacity: 0.25; cursor: default; }
.order button:hover:not(:disabled) { color: var(--color-primary); }
.children { list-style: none; margin: 0; padding: 0; border-top: 1px dashed var(--color-border); }
.add-sub { padding: 8px 12px 10px 46px; background: #fcfcfc; }
.edit { margin: 0; max-width: 260px; padding: 5px 8px; }
.edit-parent { margin: 0; width: auto; padding: 5px 8px; }
.link { background: none; border: none; color: #2673dd; cursor: pointer; font-size: 13px; padding: 0 4px; }
.link.muted { color: var(--color-text-muted); }
.link.danger { color: #d33; }
.hint { font-size: 12px; color: var(--color-text-faint); margin: 12px 0 0; }
.error { color: #d33; font-size: 13px; }
.del { min-width: min(440px, 85vw); font-size: 14px; }
.del select { width: 100%; margin: 6px 0 0; }
.btns { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }
.btn.danger { background: #d33; }
</style>
