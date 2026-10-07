import { useCallback, useEffect, useState } from 'react'
import { mealApi } from '../services/http'
import { todayInSeoul } from '../utils/date'

export function useMeals(date) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [reloadKey, setReloadKey] = useState(0)
  const reload = useCallback(() => setReloadKey((key) => key + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    let pollTimer
    let fetching = false
    let shouldPoll = date === todayInSeoul()
    setState((previous) => ({
      status: 'loading',
      data: previous.data?.date === date ? previous.data : null,
      error: null,
    }))

    async function load() {
      if (fetching || controller.signal.aborted) return
      fetching = true
      window.clearTimeout(pollTimer)
      shouldPoll = date === todayInSeoul() || shouldPoll
      try {
        const data = await mealApi.getByDate(date, controller.signal)
        if (controller.signal.aborted) return
        setState({ status: 'success', data, error: null })
        shouldPoll = date === todayInSeoul() || data.status === 'WAITING'
      } catch (error) {
        if (error.name !== 'AbortError' && !controller.signal.aborted) setState((previous) => ({ ...previous, status: 'error', error }))
      } finally {
        fetching = false
        if (shouldPoll && !controller.signal.aborted) pollTimer = window.setTimeout(poll, 60_000)
      }
    }

    function poll() {
      if (document.hidden) pollTimer = window.setTimeout(poll, 60_000)
      else load()
    }

    function onVisible() {
      if (!document.hidden && shouldPoll) load()
    }

    function onPageShow(event) {
      if (event.persisted && shouldPoll) load()
    }

    load()
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', onVisible)
    window.addEventListener('pageshow', onPageShow)
    return () => {
      controller.abort()
      window.clearTimeout(pollTimer)
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('focus', onVisible)
      window.removeEventListener('pageshow', onPageShow)
    }
  }, [date, reloadKey])

  return { ...state, reload }
}
