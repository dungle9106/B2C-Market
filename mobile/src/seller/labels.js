// "In phieu giao hang" in the seller app: the same A6 label as the web (frontend/src/views/seller/PrintLabelsView.vue),
// rendered as HTML and handed to the phone's print dialog (a printer, or "Save as PDF").
import * as Print from 'expo-print'
import { post } from '../api'
import { formatVnd } from '../format'

// Code 128 (set B), copied from frontend/src/utils/barcode.js
const PATTERNS = [
  '212222', '222122', '222221', '121223', '121322', '131222', '122213', '122312', '132212', '221213',
  '221312', '231212', '112232', '122132', '122231', '113222', '123122', '123221', '223211', '221132',
  '221231', '213212', '223112', '312131', '311222', '321122', '321221', '312212', '322112', '322211',
  '212123', '212321', '232121', '111323', '131123', '131321', '112313', '132113', '132311', '211313',
  '231113', '231311', '112133', '112331', '132131', '113123', '113321', '133121', '313121', '211331',
  '231131', '213113', '213311', '213131', '311123', '311321', '331121', '312113', '312311', '332111',
  '314111', '221411', '431111', '111224', '111422', '121124', '121421', '141122', '141221', '112214',
  '112412', '122114', '122411', '142112', '142211', '241211', '221114', '413111', '241112', '134111',
  '111242', '121142', '121241', '114212', '124112', '124211', '411212', '421112', '421211', '212141',
  '214121', '412121', '111143', '111341', '131141', '114113', '114311', '411113', '411311', '113141',
  '114131', '311141', '411131', '211412', '211214', '211232', '2331112'
]

export function code128Svg(text, height = 50) {
  const values = [104]
  for (const ch of String(text || '')) {
    const c = ch.charCodeAt(0)
    if (c >= 32 && c <= 126) values.push(c - 32)
  }
  values.push(values.reduce((sum, v, i) => sum + v * (i === 0 ? 1 : i), 0) % 103, 106)
  let x = 10
  const rects = []
  for (const v of values) {
    PATTERNS[v].split('').forEach((w, i) => {
      if (i % 2 === 0) rects.push(`<rect x="${x}" y="0" width="${w}" height="${height}"/>`)
      x += Number(w)
    })
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${x + 10} ${height}" preserveAspectRatio="none">${rects.join('')}</svg>`
}

const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))

function labelHtml(l) {
  const parts = (l.receiver || '').split(', ')
  const qty = l.items.reduce((s, i) => s + i.quantity, 0)
  const items = l.items.slice(0, 6).map(it => `<li>${esc(it.name)}${it.variant ? ' - ' + esc(it.variant) : ''} <b>x${it.quantity}</b></li>`).join('')
    + (l.items.length > 6 ? `<li class="more">... và ${l.items.length - 6} sản phẩm khác</li>` : '')
  return `
  <section class="label">
    <header class="head"><div class="carrier">${esc(l.carrierName)}<span>${esc(l.shippingMethodName)}</span></div><div class="brand">Shoppi</div></header>
    <div class="bc main">${code128Svg(l.trackingNumber)}</div>
    <div class="tracking">${esc(l.trackingNumber)}</div>
    <div class="parties">
      <div class="party"><div class="t">Từ (người gửi)</div><div class="n">${esc(l.senderName)}</div><div>${esc(l.senderPhone)}</div><div>${esc(l.senderAddress)}</div></div>
      <div class="party to"><div class="t">Đến (người nhận)</div><div class="n">${esc(parts[0])}</div><div>${esc(parts.slice(1).join(', '))}</div>
        ${l.receiverProvince ? `<div class="prov">${esc(l.receiverProvince)}</div>` : ''}</div>
    </div>
    <div class="contents"><div class="t">Nội dung hàng (${qty} sản phẩm)</div><ol>${items}</ol>${l.note ? `<div class="note">Ghi chú: ${esc(l.note)}</div>` : ''}</div>
    <footer class="foot">
      <div class="cod"><div class="t">Tiền thu người nhận</div><div class="amt">${Number(l.codAmount) > 0 ? esc(formatVnd(l.codAmount)) : '0 ₫ (đã thanh toán)'}</div></div>
      <div class="ref"><div class="bc small">${code128Svg(l.orderCode)}</div><div class="code">${esc(l.orderCode)} · ${new Date(l.createdAt).toLocaleDateString('vi-VN')}</div></div>
      <div class="sign">Chữ ký người nhận<br/><span>(Xác nhận hàng nguyên vẹn)</span></div>
    </footer>
  </section>`
}

const CSS = `
@page { size: 100mm 150mm; margin: 0; }
body { margin: 0; font-family: Arial, Helvetica, sans-serif; color: #000; }
.label { width: 100mm; height: 150mm; box-sizing: border-box; padding: 4mm; font-size: 9pt; display: flex; flex-direction: column; page-break-after: always; }
.head { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #000; padding-bottom: 2mm; }
.carrier { font-size: 13pt; font-weight: 700; } .carrier span { display: block; font-size: 8pt; font-weight: 400; }
.brand { font-size: 12pt; font-weight: 700; border: 2px solid #000; padding: 1mm 2mm; }
.bc svg { display: block; width: 100%; fill: #000; }
.bc.main { margin-top: 3mm; } .bc.main svg { height: 16mm; } .bc.small svg { height: 9mm; }
.tracking { text-align: center; font-size: 14pt; font-weight: 700; letter-spacing: 1px; margin: 1mm 0 2mm; }
.parties { display: flex; border: 1px solid #000; } .party { flex: 1; padding: 2mm; line-height: 1.3; } .party.to { border-left: 1px solid #000; }
.t { font-size: 7.5pt; text-transform: uppercase; font-weight: 700; margin-bottom: 1mm; } .n { font-weight: 700; font-size: 10pt; }
.prov { font-weight: 700; font-size: 11pt; margin-top: 1mm; }
.contents { flex: 1; border: 1px solid #000; border-top: none; padding: 2mm; overflow: hidden; }
.contents ol { margin: 0; padding-left: 5mm; line-height: 1.35; } .more { list-style: none; font-style: italic; } .note { margin-top: 1mm; font-style: italic; }
.foot { display: flex; flex-wrap: wrap; border: 1px solid #000; border-top: none; }
.cod { width: 50%; box-sizing: border-box; padding: 2mm; border-right: 1px solid #000; } .amt { font-size: 15pt; font-weight: 700; }
.ref { width: 50%; box-sizing: border-box; padding: 2mm; } .code { font-size: 7.5pt; text-align: center; }
.sign { width: 100%; border-top: 1px solid #000; padding: 2mm; height: 14mm; font-size: 8pt; font-weight: 700; box-sizing: border-box; } .sign span { font-weight: 400; font-size: 7pt; }
`

/**
 * Fetches the labels of these orders (the backend marks them printed) and opens the print dialog.
 * Resolves to { printed, skipped: [{ code, reason }] }.
 */
export async function printLabels(ids) {
  const { labels, skipped } = await post('/seller/orders/labels', undefined, { ids: ids.join(',') })
  if (labels.length) {
    const html = `<!doctype html><html><head><meta charset="utf-8"/><style>${CSS}</style></head><body>${labels.map(labelHtml).join('')}</body></html>`
    // 100 x 150 mm in points (1 mm = 2.835 pt)
    await Print.printAsync({ html, width: 283, height: 425 })
  }
  return { printed: labels.length, skipped }
}
