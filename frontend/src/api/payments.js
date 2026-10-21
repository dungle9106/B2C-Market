import http from './http'

export const getPayment = (checkoutCode) => http.get(`/payments/${checkoutCode}`)
// CARD: { cardNumber, cardHolder, expiry: 'MM/YY', cvv }
export const payCheckout = (checkoutCode, body = {}) => http.post(`/payments/${checkoutCode}/pay`, body)
export const cancelPayment = (checkoutCode) => http.post(`/payments/${checkoutCode}/cancel`)

// real gateways checkout may offer: { vnpay: boolean }
export const getPaymentMethods = () => http.get('/payments/methods')
// the signed VNPay page for this checkout: { url, txnRef, amount, expiresAt }
export const vnpayUrl = (checkoutCode) => http.post(`/payments/${checkoutCode}/vnpay`)
// forwards VNPay's query string (signed) back to the server
export const vnpayReturn = (params) => http.get('/payments/vnpay/return', { params })
