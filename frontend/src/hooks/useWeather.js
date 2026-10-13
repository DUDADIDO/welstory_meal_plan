import { useEffect, useState } from 'react'
import { solarTimesFor } from '../utils/sky'
import { weatherApi } from '../services/http'

export function useWeather(enabled = true) {
  const [weather, setWeather] = useState(null)
  useEffect(() => {
    if (!enabled) { setWeather(null); return }
    const controller = new AbortController()
    let fetching = false
    let nextCheck = 0
    let timer
    function schedule() {
      window.clearTimeout(timer)
      if (!controller.signal.aborted) timer = window.setTimeout(load, Math.max(1, nextCheck - Date.now()))
    }
    async function load() {
      if (fetching || controller.signal.aborted) return
      window.clearTimeout(timer)
      if (document.hidden) return
      if (Date.now() < nextCheck) { schedule(); return }
      fetching = true
      nextCheck = Date.now() + 600_000
      try {
        const result = await weatherApi.current(controller.signal)
        if (!controller.signal.aborted) {
          if (result.provider === 'KMA') {
            const time = Date.parse(result.fetchedAt) || Date.now()
            const solar = solarTimesFor(time, result.latitude, result.longitude)
            setWeather({ ...result, ...solar, isDay: time >= Date.parse(solar.sunrise) && time < Date.parse(solar.sunset) })
          } else setWeather(result)
        }
      } catch {
        if (!controller.signal.aborted) setWeather(null)
      } finally { fetching = false; schedule() }
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
  return weather
}
