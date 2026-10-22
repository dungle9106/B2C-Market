import http from './http'

// "Trung tâm đánh giá" (seller) - backend ReviewHubController
const B = '/seller/review-hub'

export const hubOverview = (days = 30) => http.get(`${B}/overview`, { params: { days } })
export const hubIssueTypes = () => http.get(`${B}/issue-types`)
// filters: source, rating, maxRating, sentiment, issue, level, status (OPEN|NEW|DRAFTED|REPLIED|IGNORED), anomaly, productId, overdue, q, sort
export const hubItems = (filters = {}, page = 0, size = 20) => http.get(`${B}/items`, { params: { ...clean(filters), page, size } })
export const hubItem = (id) => http.get(`${B}/items/${id}`)
export const hubDraft = (id) => http.post(`${B}/items/${id}/draft`)
export const hubSaveDraft = (id, text) => http.put(`${B}/items/${id}/draft`, { text })
export const hubReply = (id, text) => http.post(`${B}/items/${id}/reply`, { text })
export const hubIgnore = (id, note) => http.post(`${B}/items/${id}/ignore`, { note })
export const hubReopen = (id) => http.post(`${B}/items/${id}/reopen`)
export const hubSetIssues = (id, issues, note) => http.put(`${B}/items/${id}/issues`, { issues, note })
export const hubBulkDraft = (ids) => http.post(`${B}/drafts`, { ids })
export const hubSync = () => http.post(`${B}/sync`)
export const hubReanalyze = () => http.post(`${B}/reanalyze`)
export const hubImport = (file, source) => {
  const form = new FormData()
  form.append('file', file)
  form.append('source', source)
  return http.post(`${B}/import`, form)
}
export const hubTemplate = () => http.get(`${B}/import-template`, { responseType: 'blob' })
export const hubRecurring = (days = 30) => http.get(`${B}/issues`, { params: { days } })
export const hubRootCause = (key, issue, days) => http.post(`${B}/issues/root-cause`, { key, issue, days })
export const hubAnomalies = (status = 'FLAGGED', page = 0) => http.get(`${B}/anomalies`, { params: { status, page } })
export const hubDecideAnomaly = (id, action, note) => http.post(`${B}/items/${id}/anomaly`, { action, note })

// admin: the FastAPI classification model
export const reviewAiStatus = () => http.get('/admin/review-ai')
export const trainReviewAi = () => http.post('/admin/review-ai/train')

function clean(f) {
  const o = {}
  Object.entries(f).forEach(([k, v]) => { if (v !== '' && v != null && v !== false) o[k] = v })
  return o
}

export const SOURCES = { SHOPPI: 'Shoppi', SHOPEE: 'Shopee', LAZADA: 'Lazada', TIKI: 'Tiki', TIKTOK: 'TikTok Shop', OTHER: 'Khác' }
export const SOURCE_COLORS = { SHOPPI: '#ee4d2d', SHOPEE: '#f53d2d', LAZADA: '#0f146d', TIKI: '#1a94ff', TIKTOK: '#111', OTHER: '#888' }
export const LEVELS = { URGENT: 'Khẩn cấp', HIGH: 'Cao', NORMAL: 'Bình thường', LOW: 'Thấp' }
export const STATUSES = { NEW: 'Chưa xử lý', DRAFTED: 'Có bản nháp', REPLIED: 'Đã trả lời', IGNORED: 'Bỏ qua' }
export const SENTIMENTS = { POSITIVE: 'Tích cực', NEUTRAL: 'Trung lập', NEGATIVE: 'Tiêu cực' }
