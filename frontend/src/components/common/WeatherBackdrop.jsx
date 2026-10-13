import { useEffect, useLayoutEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import WeatherMoon from './WeatherMoon'
import { skyState } from '../../utils/sky'
import { ambientTheme } from '../../utils/ambientTheme'
import { moonTilt } from '../../utils/moonTilt'
import { lunarForDate, seoulDate } from '../../utils/lunarMoon'
import { useSkyTime } from '../../hooks/useSkyTime'


// Stable positions avoid reshuffling particles on each weather poll.
const particles = Array.from({ length: 300 }, (_, index) => ({
  left: `${(index * 37.7) % 115}%`, delay: `${-(index * 0.37) % 12}s`,
  speed: `${0.7 + (index % 7) * 0.1}s`, snowSpeed: `${7 + (index % 8)}s`,
}))
const stars = Array.from({ length: 46 }, (_, index) => ({
  left: `${(index * 31.73) % 100}%`, top: `${8 + (index * 19.17) % 70}%`,
  '--star-size': `${index % 5 === 0 ? 3 : 1.5}px`, animationDelay: `${-index * 0.53}s`,
}))
const glassDrops = Array.from({ length: 14 }, (_, index) => ({
  left: `${3 + (index * 23.7) % 94}%`, top: `${5 + (index * 17.3) % 85}%`,
  '--drop-size': `${7 + (index % 4) * 4}px`, animationDelay: `${-index * 1.7}s`,
  animationDuration: `${12 + (index % 5) * 3}s`,
}))

export default function WeatherBackdrop({ weather }) {
  const condition = weather?.status === 'READY' ? weather.condition : null
  const now = useSkyTime(!weather?.previewTime)
  const [pageHeight, setPageHeight] = useState(() => window.innerHeight)
  useLayoutEffect(() => {
    const root = document.getElementById('root')
    const measure = () => setPageHeight(Math.max(window.innerHeight, Math.ceil(root.getBoundingClientRect().height)))
    const observer = new ResizeObserver(measure)
    observer.observe(root)
    window.addEventListener('resize', measure)
    measure()
    return () => { observer.disconnect(); window.removeEventListener('resize', measure) }
  }, [])
  const moonDate = weather?.previewDate || seoulDate(now)
  const moonTime = weather?.previewTime ? Date.parse(weather.previewTime) : now
  const lunar = useMemo(() => lunarForDate(moonDate), [moonDate])
  const tilt = moonTilt(moonTime, lunar?.phase, weather?.latitude ?? 35.09, weather?.longitude ?? 128.87)
  const sky = skyState(weather, now)
  const night = sky.night > 0.5
  useEffect(() => {
    const root = document.documentElement
    if (condition) {
      root.dataset.weather = condition
      root.dataset.weatherNight = String(night)
      root.dataset.skyPhase = sky.phase
    } else {
      delete root.dataset.weather
      delete root.dataset.weatherNight; delete root.dataset.skyPhase
    }
    return () => { delete root.dataset.weather; delete root.dataset.weatherNight; delete root.dataset.skyPhase }
  }, [condition, night, sky.phase])

  useLayoutEffect(() => {
    if (!condition) return
    const root = document.documentElement
    const palette = ambientTheme(condition, sky)
    for (const [name, value] of Object.entries(palette.variables)) root.style.setProperty(name, value)
    root.dataset.ambientTone = palette.lightText ? 'dim' : 'bright'
    root.style.colorScheme = palette.lightText ? 'dark' : 'light'
    return () => {
      for (const name of Object.keys(palette.variables)) root.style.removeProperty(name)
      delete root.dataset.ambientTone
      root.style.removeProperty('color-scheme')
    }
  }, [condition, sky.night, sky.glow])

  if (!condition) return null
  const rain = condition === 'rain' || condition === 'storm'
  const snow = condition === 'snow'
  const storm = condition === 'storm'
  const heightScale = Math.max(1, pageHeight / window.innerHeight)
  const skyStyle = { '--weather-page-height': `${pageHeight}px`, '--moon-light': lunar?.illuminated ?? 0, '--sky-night': sky.night, '--sky-glow': sky.glow * (['rain', 'storm', 'fog'].includes(condition) ? 0.35 : 1), '--sky-sun': sky.sun, '--sun-left': `${6 + sky.sunProgress * 88}%`, '--sun-top': `${250 - Math.sin(sky.sunProgress * Math.PI) * 200}px`, '--moon-left': `${6 + sky.moonProgress * 88}%`, '--moon-top': `${250 - Math.sin(sky.moonProgress * Math.PI) * 200}px` }
  return createPortal(<>
    <div className="weather-atmosphere" data-condition={condition} data-night={night} style={skyStyle} aria-hidden="true">
      <div className="weather-night-sky" /><div className="weather-twilight-sky" />
      {condition !== 'clear' && <div className="weather-clouds"><span /><span /><span /><span /></div>}
      {condition === 'fog' && <div className="weather-fog"><span /><span /><span /></div>}
      {(rain || snow) && <div className="weather-precipitation">
        {particles.slice(0, Math.min(300, Math.ceil((snow ? 48 : storm ? 100 : 64) * heightScale))).map((drop, index) => <span key={index}
          className={snow ? 'weather-snowflake' : 'weather-raindrop'}
          style={{ left: drop.left, animationDelay: drop.delay, animationDuration: `${(snow ? parseFloat(drop.snowSpeed) : storm ? 0.38 + (index % 7) * 0.055 : parseFloat(drop.speed)) * heightScale}s`,
            ...(snow ? { width: `${3 + index % 5}px`, height: `${3 + index % 5}px` } : {}) }} />)}
      </div>}
      {storm && <div className="weather-lightning">
        <svg viewBox="0 0 140 400" preserveAspectRatio="none"><path d="M95 0 43 126 83 118 20 255 62 241 3 400 115 209 73 221 133 87 89 96 116 0" /></svg>
        <div className="weather-lightning-glow" />
      </div>}
    </div>
    <div className="weather-celestial" data-condition={condition} data-night={night} style={skyStyle} aria-hidden="true">
      <div className="weather-stars">{stars.map((style, index) => <span key={index} style={style} />)}</div>
      <div className="weather-moon"><WeatherMoon lunar={lunar} tilt={tilt} /></div>
      <div className="weather-sun"><div className="weather-sun-rays" /><div className="weather-sun-disc" /></div>
    </div>
    {rain && <div className="weather-window" style={skyStyle} data-storm={storm} aria-hidden="true">
      {glassDrops.map((style, index) => <span className="weather-glass-drop" key={index} style={style} />)}
    </div>}
  </>, document.body)
}
