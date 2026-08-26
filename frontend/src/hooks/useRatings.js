import { useCallback, useEffect, useMemo, useState } from 'react'
import { ratingApi } from '../services/http'

const CLIENT_ID_KEY = 'welstory-rating-client-id'

function createClientId() {
  try {
    const existing = window.localStorage.getItem(CLIENT_ID_KEY)
    if (existing) return existing
    const created = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
    window.localStorage.setItem(CLIENT_ID_KEY, created)
    return created
  } catch {
    return `session-${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

export function useRatings(date) {
  const clientId = useMemo(createClientId, [])
  const [ratings, setRatings] = useState({})
  const [submitting, setSubmitting] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    setRatings({})
    ratingApi.getByDate(date, clientId, controller.signal)
      .then((data) => setRatings(data.ratings || {}))
      .catch((error) => { if (error.name !== 'AbortError') setRatings({}) })
    return () => controller.abort()
  }, [date, clientId])

  const rate = useCallback(async (mealId, stars) => {
    setSubmitting(mealId)
    try {
      const summary = await ratingApi.save({ date, mealId, clientId, stars })
      setRatings((previous) => ({ ...previous, [mealId]: summary }))
    } finally {
      setSubmitting(null)
    }
  }, [date, clientId])

  return { ratings, rate, submitting }
}
