export default function WeatherBadge({ weather }) {
  if (!weather || weather.status === 'UNAVAILABLE') return null
  const time = weather.observedAt ? new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(weather.observedAt)) : ''
  const clear = weather.condition === 'clear'
  const rain = weather.condition === 'rain' || weather.condition === 'storm'
  const snow = weather.condition === 'snow'
  return <p role="status" className="mt-3 inline-flex items-center justify-center gap-1.5 text-xs text-muted"
    title={`녹산산업단지 일대 현재 날씨 · ${time} 기준 · ${weather.provider === 'KMA' ? '기상청 초단기실황(매시 관측) · 하늘 상태는 초단기예보' : weather.provider === 'PREVIEW' ? '관리자 가상 날씨' : 'Open-Meteo'} · 10분마다 확인`}>
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      {!weather.condition ? <><path d="M9 14V5a3 3 0 0 1 6 0v9a5 5 0 1 1-6 0Z" /><path d="M12 8v9" /></> : clear ? weather.isDay
        ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M2 12h2M20 12h2m-15-7 1.5 1.5m11 11 1.5 1.5m-15 0L6.5 17.5m11-11L19 5" /></>
        : <path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5 8.5 8.5 0 1 0 20.5 14.2Z" />
        : <><path d="M6 16a4 4 0 1 1 .7-7.9 5.5 5.5 0 0 1 10.7 1.4A3.3 3.3 0 1 1 18 16Z" />
          {rain && <path d="m8 19-1 2m6-2-1 2m6-2-1 2" />}
          {snow && <path d="M8 19v3m-1.5-1.5h3M16 19v3m-1.5-1.5h3" />}</>}
    </svg>
    {weather.location} · {weather.status === 'STALE' ? '날씨 갱신 지연' : <>{weather.description} · {Math.round(weather.temperature)}°C</>}
  </p>
}
