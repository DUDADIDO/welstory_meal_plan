import { useEffect, useState } from 'react'
import { weatherApi } from '../services/http'

const interval = 180_000

export function useSkyTime(enabled = true) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    let origin = Date.now(), measuredAt = performance.now(), nextCheck = 0
    let timer, fetching = false
    function schedule() {
      window.clearTimeout(timer)
      if (!controller.signal.aborted) timer = window.setTimeout(load, Math.max(1, nextCheck - performance.now()))
    }
    async function load() {
      if (controller.signal.aborted || fetching) return
      window.clearTimeout(timer)
      if (document.hidden) return
      if (performance.now() < nextCheck) { schedule(); return }
      fetching = true
      const started = performance.now()
      nextCheck = started + interval
      try {
        const result = await weatherApi.time(AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]))
        const serverTime = Date.parse(result.serverTime)
        if (Number.isFinite(serverTime)) {
          measuredAt = performance.now()
          origin = serverTime + (measuredAt - started) / 2
        }
      } catch { /* Keep advancing the last synchronized clock if a request fails. */ }
      finally {
        if (!controller.signal.aborted) setNow(origin + performance.now() - measuredAt)
        fetching = false
        schedule()
      }
    }
    load()
    document.addEventListener('visibilitychange', load)
    window.addEventListener('focus', load)
    window.addEventListener('pageshow', load)
    return () => {
      controller.abort()
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', load)
      window.removeEventListener('focus', load)
      window.removeEventListener('pageshow', load)
    }
  }, [enabled])
  return now
}
