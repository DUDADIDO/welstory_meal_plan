import { useCallback, useEffect, useState } from 'react'

const THEME_KEY = 'welstory-theme'

function systemPrefersDark() {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

function getInitialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)

    if (saved === 'dark' || saved === 'light') {
      return saved
    }
  } catch {
    // ignore
  }

  return systemPrefersDark() ? 'dark' : 'light'
}

function applyTheme(theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}

export function useTheme() {
  const [theme, setTheme] = useState(getInitialTheme)

  useEffect(() => {
    applyTheme(theme)

    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      // ignore
    }
  }, [theme])

  const toggleTheme = useCallback(() => {
    setTheme((current) => (
      current === 'dark'
        ? 'light'
        : 'dark'
    ))
  }, [])

  return {
    theme,
    dark: theme === 'dark',
    toggleTheme,
  }
}