export const NOTIFICATION_TYPES = [
  { value: 'ORDER', label: 'Cập nhật đơn hàng', icon: '📦' },
  { value: 'PROMOTION', label: 'Khuyến mãi', icon: '🎁' },
  { value: 'MESSAGE', label: 'Tin nhắn', icon: '💬' },
  { value: 'SUPPORT', label: 'Hỗ trợ', icon: '🛟' },
  { value: 'SYSTEM', label: 'Shoppi', icon: '🔔' }
]

export const notificationIcon = (type) => NOTIFICATION_TYPES.find(t => t.value === type)?.icon || '🔔'

// "5 phút trước", "Hôm qua 14:20", "02/10/2026"
export function timeAgo(value) {
  if (!value) return ''
  const d = new Date(value)
  const diff = (Date.now() - d.getTime()) / 1000
  if (diff < 60) return 'Vừa xong'
  if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`
  if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`
  const time = d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  if (diff < 2 * 86400) return `Hôm qua ${time}`
  if (diff < 7 * 86400) return `${Math.floor(diff / 86400)} ngày trước`
  return d.toLocaleDateString('vi-VN')
}
