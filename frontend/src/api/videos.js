import http from './http'

// "Shoppi Video" (backend ShortVideoController)
export const videoFeed = ({ sort = 'hot', productId, shopId, page = 0, size = 10 } = {}) =>
  http.get('/videos', { params: { sort, productId, shopId, page, size } })
export const countVideoView = (id) => http.post(`/videos/${id}/view`)
export const likeVideo = (id) => http.post(`/buyer/videos/${id}/like`)
export const unlikeVideo = (id) => http.delete(`/buyer/videos/${id}/like`)

export const myVideos = () => http.get('/seller/videos')
export const createVideo = (body) => http.post('/seller/videos', body)          // { videoUrl, caption, productIds }
export const updateVideo = (id, body) => http.put(`/seller/videos/${id}`, body)  // { caption, productIds }
export const deleteVideo = (id) => http.delete(`/seller/videos/${id}`)

export const adminVideos = ({ status = 'ACTIVE', page = 0, size = 20 } = {}) => http.get('/admin/videos', { params: { status, page, size } })
export const hideVideo = (id, reason) => http.post(`/admin/videos/${id}/hide`, { reason })
export const restoreVideo = (id) => http.post(`/admin/videos/${id}/restore`)
