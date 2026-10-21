export function formatVnd(value) {
  return '₫' + new Intl.NumberFormat('vi-VN').format(value || 0)
}

export function formatDateTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' })
}

// 1234 -> "1,2k" like Shopee's "Đã bán 1,2k"
export function formatCount(n) {
  const v = Number(n || 0)
  if (v < 1000) return String(v)
  return (Math.floor(v / 100) / 10).toLocaleString('vi-VN') + 'k'
}

// for <input type="datetime-local">: "2026-10-01T13:30" in local time
export function toLocalInput(value) {
  if (!value) return ''
  const d = new Date(value)
  const pad = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
