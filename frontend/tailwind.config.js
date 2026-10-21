/** @type {import('tailwindcss').Config} */
// Tailwind is used next to the existing hand-written CSS: utilities carry a "tw-" prefix so they never clash with
// class names already in use (.grid, .block, .card...), and preflight is off so the current base styles stay.
export default {
  prefix: 'tw-',
  content: ['./index.html', './src/**/*.{vue,js}'],
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: '#ee4d2d', light: '#fff1ee', dark: '#d73211' },
        vnpay: '#005baa'
      }
    }
  },
  plugins: []
}
