const linkClass = 'underline decoration-current/30 underline-offset-2 hover:decoration-current'

export default function WeatherAttribution({ weather }) {
  if (weather?.provider === 'PREVIEW') return <span>날씨 미리보기</span>
  const published = weather?.observedAt && Number.isFinite(Date.parse(weather.observedAt)) ? new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric' }).format(new Date(weather.observedAt)) : ''
  if (weather?.provider === 'KMA') return <>
    <a href="https://www.data.go.kr/data/15084084/openapi.do" target="_blank" rel="noopener noreferrer" className={linkClass}>기상청 · 단기예보 조회서비스{published && ` (${published} 자료)`}</a>
    {' / '}
    <a href="https://www.kogl.or.kr/info/licenseType1.do" target="_blank" rel="noopener noreferrer" className={linkClass}>공공누리 제1유형 · 출처표시</a>
    {' (날씨 표시·배경 효과로 가공)'}
  </>
  return <a href="https://open-meteo.com/" target="_blank" rel="noopener noreferrer" className={linkClass}>Open-Meteo</a>
}
