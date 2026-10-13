import { lunarForDate, seoulDate } from './lunarMoon'

export const weatherPresets = [
  { value: 'clear', label: '맑음', temperature: 25 },
  { value: 'cloudy', label: '흐림', temperature: 21 },
  { value: 'fog', label: '안개', temperature: 17 },
  { value: 'rain', label: '비', temperature: 18 },
  { value: 'snow', label: '눈', temperature: -1 },
  { value: 'storm', label: '뇌우', temperature: 19 },
]

export function isWeatherPreview(value) {
  return value === 'current' || weatherPresets.some(preset => preset.value === value)
}

export function previewSnapshot(condition, night, time, date) {
  const preset = weatherPresets.find(value => value.value === condition)
  if (!preset) return null
  const day = lunarForDate(date) ? date : seoulDate(Date.now())
  const validTime = /^([01]\d|2[0-3]):[0-5]\d$/.test(time || '') ? time : night ? '22:00' : '12:00'
  return {
    provider: 'PREVIEW', latitude: 35.09, longitude: 128.87, previewDate: day, sunrise: `${day}T06:30:00+09:00`, sunset: `${day}T18:00:00+09:00`, previewTime: `${day}T${validTime}:00+09:00`,
    location: '녹산산업단지', status: 'READY', condition, description: preset.label,
    temperature: preset.temperature, isDay: validTime >= '06:30' && validTime < '18:00', precipitation: ['rain', 'storm'].includes(condition) ? 1.2 : 0,
    cloudCover: condition === 'clear' ? 0 : 100, observedAt: new Date().toISOString(),
  }
}
