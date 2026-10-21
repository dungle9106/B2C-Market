// Code 128 (set B) barcodes as SVG, for shipping labels: tracking numbers and order codes
// are plain ASCII ("GHN482910375", "ORD-20261002-12345"). No library needed.

// bar/space widths of each code value 0-106 (103-105 = start A/B/C, 106 = stop)
const PATTERNS = [
  '212222', '222122', '222221', '121223', '121322', '131222', '122213', '122312', '132212', '221213',
  '221312', '231212', '112232', '122132', '122231', '113222', '123122', '123221', '223211', '221132',
  '221231', '213212', '223112', '312131', '311222', '321122', '321221', '312212', '322112', '322211',
  '212123', '212321', '232121', '111323', '131123', '131321', '112313', '132113', '132311', '211313',
  '231113', '231311', '112133', '112331', '132131', '113123', '113321', '133121', '313121', '211331',
  '231131', '213113', '213311', '213131', '311123', '311321', '331121', '312113', '312311', '332111',
  '314111', '221411', '431111', '111224', '111422', '121124', '121421', '141122', '141221', '112214',
  '112412', '122114', '122411', '142112', '142211', '241211', '221114', '413111', '241112', '134111',
  '111242', '121142', '121241', '114212', '124112', '124211', '411212', '421112', '421211', '212141',
  '214121', '412121', '111143', '111341', '131141', '114113', '114311', '411113', '411311', '113141',
  '114131', '311141', '411131', '211412', '211214', '211232', '2331112'
]
const START_B = 104
const STOP = 106

/** Code values for text (start B, data, checksum, stop). Throws on non-printable-ASCII characters. */
export function code128Values(text) {
  const values = [START_B]
  for (const ch of text) {
    const c = ch.charCodeAt(0)
    if (c < 32 || c > 126) throw new Error(`Ký tự không hỗ trợ trong mã vạch: ${ch}`)
    values.push(c - 32)
  }
  const checksum = values.reduce((sum, v, i) => sum + v * (i === 0 ? 1 : i), 0) % 103
  values.push(checksum, STOP)
  return values
}

/** SVG markup of the barcode; width scales with the container (viewBox), height fixed by the caller's CSS. */
export function code128Svg(text, { height = 50 } = {}) {
  const quiet = 10
  let x = quiet
  const rects = []
  for (const v of code128Values(text)) {
    PATTERNS[v].split('').forEach((w, i) => {
      const width = Number(w)
      if (i % 2 === 0) rects.push(`<rect x="${x}" y="0" width="${width}" height="${height}"/>`)
      x += width
    })
  }
  const total = x + quiet
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${total} ${height}" preserveAspectRatio="none" role="img" aria-label="${text}">${rects.join('')}</svg>`
}
