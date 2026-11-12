import http from './http'

// "Yeu thich" products and "Theo doi" shops (backend SocialController)
export const productLikes = (productId) => http.get(`/products/${productId}/likes`)   // { count, liked }
export const likeProduct = (productId) => http.post(`/buyer/likes/${productId}`)
export const unlikeProduct = (productId) => http.delete(`/buyer/likes/${productId}`)
export const myLikes = (page = 0, size = 24) => http.get('/buyer/likes', { params: { page, size } })

export const shopFollowers = (shopId) => http.get(`/shops/${shopId}/followers`)        // { count, following, followerVouchers }
export const followShop = (shopId) => http.post(`/buyer/follows/${shopId}`)
export const unfollowShop = (shopId) => http.delete(`/buyer/follows/${shopId}`)
export const myFollows = (page = 0, size = 24) => http.get('/buyer/follows', { params: { page, size } })
