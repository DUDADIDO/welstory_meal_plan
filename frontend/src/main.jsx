import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import './assets/styles/index.css'

const THEME_KEY = 'welstory-theme'

try {
  const savedTheme = localStorage.getItem(THEME_KEY)

  const prefersDark = window.matchMedia(
    '(prefers-color-scheme: dark)',
  ).matches

  const shouldUseDark =
    savedTheme === 'dark'
    || (!savedTheme && prefersDark)

  document.documentElement.classList.toggle(
    'dark',
    shouldUseDark,
  )
} catch {
  // localStorage 사용 불가능 시 기본 라이트모드
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)