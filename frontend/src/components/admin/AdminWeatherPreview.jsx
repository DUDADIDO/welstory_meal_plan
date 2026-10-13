import { lunarForDate } from '../../utils/lunarMoon'
import { useEffect, useLayoutEffect, useMemo, useState } from 'react'
import MainPage from '../../pages/MainPage'
import { isWeatherPreview, previewSnapshot } from '../../utils/weatherPreview'

export default function AdminWeatherPreview({ condition, night, time, date }) {
  const [live, setLive] = useState(null)
  useEffect(() => {
    if (window.parent === window) return
    const receive = event => {
      if (event.origin !== window.location.origin || event.source !== window.parent || event.data?.type !== 'weather-preview-settings') return
      const value = event.data
      if (!isWeatherPreview(value.condition) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(value.time || '')) return
      if (value.date && !lunarForDate(value.date)) return
      setLive({ condition: value.condition, time: value.time, date: value.date })
    }
    window.addEventListener('message', receive)
    window.parent.postMessage({ type: 'weather-preview-ready' }, window.location.origin)
    return () => window.removeEventListener('message', receive)
  }, [])
  const selectedCondition = live?.condition ?? condition
  const selectedTime = live?.time ?? time
  const selectedDate = live?.date ?? date
  const weather = useMemo(() => previewSnapshot(selectedCondition, night, selectedTime, selectedDate), [selectedCondition, night, selectedTime, selectedDate])
  useLayoutEffect(() => {
    const root = document.documentElement
    root.dataset.weatherPreview = 'true'
    return () => { delete root.dataset.weatherPreview }
  }, [])
  return <div className="min-h-dvh text-ink antialiased"><MainPage previewMode weatherOverride={weather} /></div>
}
