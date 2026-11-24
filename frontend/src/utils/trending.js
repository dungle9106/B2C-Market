import { hotKeywords } from '../api/products'

// the header is on every page: fetch the hot keywords once per page load, not once per page
let cached = null

export function loadHotKeywords() {
  if (!cached) {
    cached = hotKeywords(10).then(({ data }) => data).catch(() => {
      cached = null
      return []
    })
  }
  return cached
}
