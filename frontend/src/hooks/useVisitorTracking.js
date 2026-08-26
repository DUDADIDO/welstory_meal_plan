import { useEffect } from 'react'
import { visitorApi } from '../services/http'

const CLIENT_ID_KEY = 'welstory-visitor-id'

function getVisitorId() {
  try {
    const existing =
      window.localStorage.getItem(CLIENT_ID_KEY)

    if (existing) {
      return existing
    }

    const created =
      window.crypto?.randomUUID?.()
      || `${Date.now()}-${Math.random()
        .toString(36)
        .slice(2)}`

    window.localStorage.setItem(
      CLIENT_ID_KEY,
      created,
    )

    return created
  } catch {
    return `session-${Date.now()}-${Math.random()
      .toString(36)
      .slice(2)}`
  }
}

export function useVisitorTracking() {
  useEffect(() => {
    const visitorId = getVisitorId()

    visitorApi
      .record(visitorId)
      .catch(() => {})
  }, [])
}