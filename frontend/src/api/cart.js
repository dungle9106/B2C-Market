import http from './http'

export const getCart = () => http.get('/cart')
export const addToCart = (data) => http.post('/cart', data)
export const updateCartQuantity = (id, quantity) => http.patch(`/cart/${id}`, { quantity })
export const removeFromCart = (id) => http.delete(`/cart/${id}`)
export const removeManyFromCart = (ids) => http.delete('/cart', { params: { ids: ids.join(',') } })
