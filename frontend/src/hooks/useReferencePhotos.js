import { useEffect, useState } from 'react'
import { menuHistoryApi } from '../services/http'

export function useReferencePhotos(date, meals) {
  const missing = (meals || []).filter((meal) => !meal.imageUrl).map((meal) => `${meal.id}:${meal.name}`).join('|')
  const [state, setState] = useState({ date: null, photos: {} })

  useEffect(() => {
    const controller = new AbortController()
    let timer
    setState({ date, photos: {} })
    async function load() {
      try {
        const photos = await menuHistoryApi.references(date, controller.signal)
        if (!controller.signal.aborted) setState({ date, photos })
      } catch { /* A missing reference does not block the current meal page. */ }
      finally {
        if (!controller.signal.aborted) timer = window.setTimeout(load, 120_000)
      }
    }
    if (missing) load()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [date, missing])

  return state.date === date ? state.photos : {}
}
