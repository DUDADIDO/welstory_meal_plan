export const KOREA_TIMEZONE = 'Asia/Seoul'
export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function asSeoulDate(isoDate) {
  return new Date(`${isoDate}T12:00:00+09:00`)
}

export function todayInSeoul() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: KOREA_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

export function moveDate(isoDate, amount) {
  const date = asSeoulDate(isoDate)
  date.setDate(date.getDate() + amount)
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: KOREA_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function formatDate(isoDate) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: KOREA_TIMEZONE,
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(asSeoulDate(isoDate))
}

export function formatShortDate(isoDate) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: KOREA_TIMEZONE,
    month: 'short',
    day: 'numeric',
  }).format(asSeoulDate(isoDate))
}

export function formatMonth(year, month) {
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long' })
    .format(new Date(year, month - 1, 1))
}

export function formatTime(instant) {
  if (!instant) return null
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: KOREA_TIMEZONE,
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(instant))
}

export function monthParts(monthKey) {
  const [year, month] = monthKey.split('-').map(Number)
  return { year, month }
}

export function moveMonth(monthKey, amount) {
  const { year, month } = monthParts(monthKey)
  const moved = new Date(year, month - 1 + amount, 1)
  return `${moved.getFullYear()}-${String(moved.getMonth() + 1).padStart(2, '0')}`
}
