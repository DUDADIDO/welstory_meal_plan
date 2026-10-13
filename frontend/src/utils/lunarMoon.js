import KoreanLunarCalendar from 'korean-lunar-calendar'

export const seoulDate = timestamp => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date(timestamp))

export function lunarForDate(date) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '')) return null
  const calendar = new KoreanLunarCalendar()
  const [year, month, day] = date.split('-').map(Number)
  if (!calendar.setSolarDate(year, month, day)) return null
  const lunar = calendar.getLunarCalendar()
  const monthLength = calendar.setLunarDate(lunar.year, lunar.month, 30, lunar.intercalation) ? 30 : 29
  // Calendar-based illustration: first day is new, fifteenth is full.
  const phase = lunar.day <= 15 ? (lunar.day - 1) / 28 : 0.5 + (lunar.day - 15) / (2 * (monthLength - 14))
  return { ...lunar, monthLength, phase, illuminated: (1 - Math.cos(phase * 2 * Math.PI)) / 2 }
}

export function dateForLunarDay(date, day) {
  const lunar = lunarForDate(date)
  if (!lunar) return null
  const calendar = new KoreanLunarCalendar()
  if (!calendar.setLunarDate(lunar.year, lunar.month, day, lunar.intercalation)) return null
  const solar = calendar.getSolarCalendar()
  return `${solar.year}-${String(solar.month).padStart(2, '0')}-${String(solar.day).padStart(2, '0')}`
}

export function moonLitPath(phase) {
  if (!Number.isFinite(phase) || phase <= 0 || phase >= 1) return ''
  const cosine = Math.cos(phase * 2 * Math.PI)
  const radius = Math.abs(cosine * 48)
  const waxing = phase < 0.5
  const outerSweep = waxing ? 1 : 0
  const terminatorSweep = waxing ? (cosine >= 0 ? 0 : 1) : (cosine >= 0 ? 1 : 0)
  return `M50 2 A48 48 0 0 ${outerSweep} 50 98 ${radius < 0.0001 ? 'L50 2' : `A${radius.toFixed(4)} 48 0 0 ${terminatorSweep} 50 2`} Z`
}
