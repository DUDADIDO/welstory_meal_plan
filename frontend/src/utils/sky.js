import { getTimes } from 'suncalc'

const minute = 60_000
const clamp = value => Math.max(0, Math.min(1, value))
const smooth = value => { const t = clamp(value); return t * t * (3 - 2 * t) }

// Time comes from today's local sunrise/sunset, never the selected meal date.
export function skyState(weather, now = Date.now()) {
  const time = weather?.previewTime ? Date.parse(weather.previewTime) : now
  const sunrise = Date.parse(weather?.sunrise), sunset = Date.parse(weather?.sunset)
  if (!Number.isFinite(sunrise) || !Number.isFinite(sunset) || sunset <= sunrise || time < sunrise - 12 * 60 * minute || time > sunset + 12 * 60 * minute) {
    return { sunProgress: 0.5, moonProgress: 0.5, night: weather?.isDay === false ? 1 : 0, glow: 0, phase: weather?.isDay === false ? 'night' : 'day', sun: weather?.isDay === false ? 0 : 1 }
  }
  const day = 24 * 60 * minute
  const sunProgress = clamp((time - sunrise) / (sunset - sunrise))
  const moonrise = time < sunrise + 40 * minute ? sunset - day : sunset
  const moonset = time < sunrise + 40 * minute ? sunrise : sunrise + day
  // Decorative night arc: sunset to next sunrise (not a lunar ephemeris).
  const moonProgress = clamp((time - moonrise) / (moonset - moonrise))
  const morning = smooth((time - (sunrise - 80 * minute)) / (100 * minute))
  const evening = smooth((time - (sunset - 20 * minute)) / (100 * minute))
  const night = 1 - morning * (1 - evening)
  const sunsetGlow = smooth((time - (sunset - 70 * minute)) / (55 * minute)) * (1 - smooth((time - (sunset + 20 * minute)) / (60 * minute)))
  const sunriseGlow = smooth((time - (sunrise - 80 * minute)) / (60 * minute)) * (1 - smooth((time - (sunrise + 15 * minute)) / (55 * minute)))
  const glow = Math.max(sunsetGlow, sunriseGlow)
  const sun = smooth((time - (sunrise - 5 * minute)) / (25 * minute)) * (1 - smooth((time - (sunset - 5 * minute)) / (25 * minute)))
  return { night, glow, sun, sunProgress, moonProgress, phase: night > 0.65 ? 'night' : glow > 0.2 ? 'twilight' : 'day' }
}

export function solarTimesFor(timestamp, latitude = 35.09, longitude = 128.87) {
  if (!Number.isFinite(timestamp)) return { sunrise: null, sunset: null }
  const day = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date(timestamp))
  const times = getTimes(new Date(`${day}T12:00:00+09:00`), latitude, longitude)
  return {
    sunrise: times.sunrise && Number.isFinite(+times.sunrise) ? times.sunrise.toISOString() : null,
    sunset: times.sunset && Number.isFinite(+times.sunset) ? times.sunset.toISOString() : null,
  }
}
