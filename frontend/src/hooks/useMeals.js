import { useCallback, useEffect, useState } from 'react'
import { mealApi } from '../services/http'

export function useMeals(date) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => setReloadKey((key) => key + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    let pollTimer
    setState({ status: 'loading', data: null, error: null })

    async function load() {
      try {
        const data = await mealApi.getByDate(date, controller.signal)
        setState({ status: 'success', data, error: null })
        if (data.status === 'WAITING') pollTimer = window.setTimeout(load, 60_000)
      } catch (error) {
        if (error.name !== 'AbortError') setState((previous) => ({ ...previous, status: 'error', error }))
      }
    }

    load()
    return () => {
      controller.abort()
      window.clearTimeout(pollTimer)
    }
  }, [date, reloadKey])

  return { ...state, reload }
}
