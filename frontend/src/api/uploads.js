import http from './http'

// returns { url: '/uploads/<random>.<ext>' }
export const uploadFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/uploads', form)
}
