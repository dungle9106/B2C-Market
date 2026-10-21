// Product variants in the seller's form (VariantEditor) <-> the API.
//
// form model: {
//   tiers: [{ uid, name, options: [{ uid, value, imageUrl }] }],   0, 1 or 2 groups ("Màu sắc", "Kích cỡ")
//   rows:  [{ key, uid1, uid2, id, extraPrice, stock, sku }]       one per combination
// }
// API: { tier1Name, tier2Name, variants: [{ id, option1, option2, extraPrice, stock, sku, imageUrl }] }

let uid = 0
export const newUid = () => ++uid

export const emptyVariantModel = () => ({ tiers: [], rows: [] })

/** From a product returned by /api/seller/products/{id}. Old flat variants become one group "Phân loại". */
export function toVariantModel(p) {
  if (!p.variants?.length) return emptyVariantModel()
  const names = [p.tier1Name || 'Phân loại', p.tier2Name].filter(Boolean)
  const tiers = names.map(name => ({ uid: newUid(), name, options: [] }))
  const optionUid = (t, value, imageUrl) => {
    let o = tiers[t].options.find(x => x.value === value)
    if (!o) {
      o = { uid: newUid(), value, imageUrl: imageUrl || null }
      tiers[t].options.push(o)
    } else if (!o.imageUrl && imageUrl) {
      o.imageUrl = imageUrl
    }
    return o.uid
  }
  const rows = p.variants.map(v => {
    const uid1 = optionUid(0, p.tier1Name ? v.option1 : v.name, v.imageUrl)
    const uid2 = tiers[1] ? optionUid(1, v.option2) : null
    return { key: `${uid1}|${uid2 ?? ''}`, uid1, uid2, id: v.id, extraPrice: Number(v.extraPrice || 0), stock: v.stock, sku: v.sku || '' }
  })
  return { tiers, rows }
}

/** Problems the seller must fix before saving, or '' when fine. */
export function validateVariantModel(m) {
  for (const [i, t] of m.tiers.entries()) {
    if (!t.name.trim()) return `Vui lòng nhập tên nhóm phân loại ${i + 1}`
    const values = t.options.map(o => o.value.trim().toLowerCase())
    if (values.some(v => !v)) return `Vui lòng nhập đủ giá trị cho nhóm "${t.name.trim()}"`
    if (new Set(values).size !== values.length) return `Nhóm "${t.name.trim()}" có giá trị bị trùng`
  }
  if (m.tiers.length === 2 && m.tiers[0].name.trim().toLowerCase() === m.tiers[1].name.trim().toLowerCase()) {
    return 'Hai nhóm phân loại phải có tên khác nhau'
  }
  if (m.rows.some(r => !(r.stock >= 0))) return 'Kho của phân loại không hợp lệ'
  if (m.rows.some(r => !(Number(r.extraPrice || 0) >= 0))) return 'Giá cộng thêm không hợp lệ'
  return ''
}

export function fromVariantModel(m) {
  const [t1, t2] = m.tiers
  if (!t1) return { tier1Name: null, tier2Name: null, variants: [] }
  const opt = (t, id) => t?.options.find(o => o.uid === id)
  return {
    tier1Name: t1.name.trim(),
    tier2Name: t2 ? t2.name.trim() : null,
    variants: m.rows.map(r => ({
      id: r.id,
      option1: opt(t1, r.uid1).value.trim(),
      option2: t2 ? opt(t2, r.uid2).value.trim() : null,
      imageUrl: opt(t1, r.uid1).imageUrl || null,
      extraPrice: Number(r.extraPrice) || 0,
      stock: Number(r.stock) || 0,
      sku: r.sku || null
    }))
  }
}
