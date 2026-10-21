import http from './http'

// public platform policies: TERMS | PRIVACY | RETURN | SELLER | PROHIBITED (case-insensitive)
export const policies = () => http.get('/policies')
export const policy = (key, version) => http.get(`/policies/${key}`, { params: { version: version || undefined } })
