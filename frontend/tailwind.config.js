/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: { sans: ['Inter', 'system-ui', 'Segoe UI', 'sans-serif'], mono: ['JetBrains Mono', 'Consolas', 'monospace'] },
      colors: {
        ink: { 950: '#070b14', 900: '#0b1220', 850: '#0f1729', 800: '#131d33', 700: '#1c2944', 600: '#2a3a5c' },
      },
      keyframes: {
        ping2: { '0%': { transform: 'scale(1)', opacity: '0.8' }, '100%': { transform: 'scale(2.6)', opacity: '0' } },
        slidein: { '0%': { transform: 'translateY(-6px)', opacity: '0' }, '100%': { transform: 'translateY(0)', opacity: '1' } },
      },
      animation: { ping2: 'ping2 1.6s cubic-bezier(0,0,0.2,1) infinite', slidein: 'slidein .35s ease-out' },
    },
  },
  plugins: [],
}
