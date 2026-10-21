// backend errors come either as { message } or, for bean validation, { errors: { field: msg } }
export function extractError(e) {
  const data = e.response?.data
  if (data?.message) return data.message
  if (data?.errors) return Object.values(data.errors)[0]
  // no response at all, or the Vite proxy's empty 5xx when the backend is down/still starting
  if (!e.response || e.response.status >= 500) {
    return 'Không kết nối được máy chủ. Vui lòng kiểm tra backend đã chạy xong chưa rồi thử lại.'
  }
  return 'Đã có lỗi xảy ra, vui lòng thử lại'
}
