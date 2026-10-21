export const BUSINESS_TYPES = {
  INDIVIDUAL: 'Cá nhân',
  HOUSEHOLD: 'Hộ kinh doanh',
  COMPANY: 'Doanh nghiệp'
}

export const VERIFICATION_STATUS = {
  PENDING: 'Đang chờ duyệt',
  APPROVED: 'Đã xác minh',
  REJECTED: 'Bị từ chối'
}

export const RETURN_DAY_OPTIONS = [0, 3, 7, 15, 30]
export const returnDaysLabel = (d) => (d > 0 ? `Đổi trả trong ${d} ngày` : 'Không hỗ trợ đổi trả')

// "Tham gia 3 tháng trước" on the shop page
export function joinedAgo(value) {
  if (!value) return ''
  const days = Math.floor((Date.now() - new Date(value).getTime()) / 86400000)
  if (days < 1) return 'Hôm nay'
  if (days < 30) return `${days} ngày trước`
  if (days < 365) return `${Math.floor(days / 30)} tháng trước`
  return `${Math.floor(days / 365)} năm trước`
}
