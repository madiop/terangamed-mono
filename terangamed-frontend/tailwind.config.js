/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      // Tokens TerangaMed alignés sur le design existant.
      // Synchronisés avec les CSS variables --color-* de styles.scss.
      colors: {
        // Couleurs métier
        'tm-primary': {
          DEFAULT: '#3b82f6',
          dark: '#2563eb',
          50: '#EFF6FF', 100: '#DBEAFE', 500: '#3B82F6', 600: '#2563EB'
        },
        'tm-success': '#10b981',
        'tm-warning': '#f59e0b',
        'tm-danger': '#ef4444',

        // Surfaces / textes
        'tm-bg': '#f1f5f9',
        'tm-surface': '#ffffff',
        'tm-border': '#e2e8f0',
        'tm-text': '#0f172a',
        'tm-text-muted': '#64748b',

        // Sidebar (slate-800 family)
        'tm-sidebar': {
          bg: '#1e293b',
          text: '#e2e8f0',
          'text-muted': '#94a3b8',
          icon: '#64748b'
        }
      },
      fontFamily: {
        sans: [
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'sans-serif'
        ]
      },
      borderRadius: {
        card: '14px'
      },
      boxShadow: {
        card: '0 1px 4px rgba(0, 0, 0, 0.06)',
        'card-hover': '0 4px 12px rgba(0, 0, 0, 0.10)'
      }
    }
  },
  plugins: []
};
