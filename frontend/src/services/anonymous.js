import { chatApi } from './http'

let pendingIdentity
let memoryKey
const STORAGE_KEY = 'welstory-anonymous-browser-key'

function browserKey() {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (/^[A-Za-z0-9_-]{43}$/.test(stored || '')) return stored
  } catch { /* Restricted storage still allows cookie-based participation. */ }
  if (!memoryKey) {
    const bytes = window.crypto.getRandomValues(new Uint8Array(32))
    memoryKey = btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
  }
  try { window.localStorage.setItem(STORAGE_KEY, memoryKey) } catch { /* Keep a key for this tab. */ }
  return memoryKey
}

// Recover the server identity after cookie deletion using an independent browser secret.
export function ensureAnonymousUser() {
  if (!pendingIdentity) {
    pendingIdentity = chatApi.identity(browserKey()).finally(() => { pendingIdentity = null })
  }
  return pendingIdentity
}
