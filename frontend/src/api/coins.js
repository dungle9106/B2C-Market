import http from './http'

// "Shoppi Xu" (backend CoinController)
export const myCoins = () => http.get('/buyer/coins')   // { balance, checkedInToday, streak, todayReward, rewards, earnPercent, maxSpendPercent }
export const checkIn = () => http.post('/buyer/coins/check-in')
export const coinHistory = (page = 0, size = 20) => http.get('/buyer/coins/history', { params: { page, size } })

// "Vi ShoppiPay" and "Hang thanh vien" (backend ShoppiPayController)
export const myWallet = () => http.get('/buyer/wallet')
export const topUpWallet = (amount) => http.post('/buyer/wallet/top-up', { amount })
export const walletHistory = (page = 0, size = 20) => http.get('/buyer/wallet/history', { params: { page, size } })
export const myMembership = () => http.get('/buyer/membership')
