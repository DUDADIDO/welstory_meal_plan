import { dateForLunarDay, lunarForDate, seoulDate } from '../../utils/lunarMoon'
import { useCallback, useEffect, useRef, useState } from 'react'
import { weatherPresets } from '../../utils/weatherPreview'

const periods = { dawn: '06:15', day: '12:00', sunset: '18:00', evening: '18:45', night: '22:00' }
const toMinutes = time => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5))
const toTime = minutes => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(Math.floor(minutes % 60)).padStart(2, '0')}`

export default function WeatherTester() {
  const [date, setDate] = useState(() => seoulDate(Date.now()))
  const lunar = lunarForDate(date)
  const moonDays = [1, 4, 8, 15, 22, 27]
  const [condition, setCondition] = useState('current')
  const [minutes, setMinutes] = useState(720)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState(900)
  const iframe = useRef(null)
  const time = toTime(minutes)
  const period = Object.keys(periods).find(key => periods[key] === time) || 'custom'
  const settings = useRef(null)
  settings.current = { type: 'weather-preview-settings', condition, time, date }
  const sendSettings = useCallback(() => iframe.current?.contentWindow?.postMessage(settings.current, window.location.origin), [])

  useEffect(() => { sendSettings() }, [condition, time, date, sendSettings])
  useEffect(() => {
    const ready = event => {
      if (event.origin === window.location.origin && event.source === iframe.current?.contentWindow && event.data?.type === 'weather-preview-ready') sendSettings()
    }
    window.addEventListener('message', ready)
    return () => window.removeEventListener('message', ready)
  }, [sendSettings])
  useEffect(() => {
    if (!playing || condition === 'current') return
    let frame, last = performance.now()
    const tick = now => {
      if (document.hidden) last = now
      else if (now - last >= 100) {
        const elapsed = now - last
        last = now
        setMinutes(value => (value + elapsed * speed / 60_000) % 1440)
      }
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [playing, speed, condition])

  function changeTime(value) { setPlaying(false); setMinutes(toMinutes(value)) }
  function startDay() { setCondition(value => value === 'current' ? 'clear' : value); setMinutes(0); setPlaying(true) }
  function changeCondition(value) { setCondition(value); if (value === 'current') setPlaying(false) }
  const query = new URLSearchParams({ weatherPreview: condition, previewTime: time, previewDate: date })
  const selectClass = 'rounded-xl bg-soft px-3 py-2.5 text-sm outline-none ring-1 ring-[var(--theme-border)] focus:ring-apple-blue disabled:opacity-50'
  const buttonClass = 'rounded-xl bg-soft px-3 py-2.5 text-xs font-semibold ring-1 ring-[var(--theme-border)] disabled:opacity-40'
  const actual = condition === 'current'

  return <section aria-labelledby="weather-tester-title" className="mt-8 overflow-hidden rounded-3xl bg-surface shadow-sm ring-1 ring-[var(--theme-border)]">
    <header className="border-b border-[var(--theme-border)] px-5 py-5 sm:px-6">
      <h2 id="weather-tester-title" className="text-lg font-bold">날씨 배경 테스트</h2>
      <p className="mt-1 text-xs leading-5 text-muted">날씨와 시간을 바꾸거나 배속으로 재생해 해·달의 이동, 석양, 밤의 밝기와 날씨 효과를 확인하세요.</p>
      <div className="mt-4 flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1.5 text-xs font-medium text-muted">날씨
          <select aria-label="테스트 날씨" value={condition} onChange={event => changeCondition(event.target.value)} className={selectClass}>
            <option value="current">현재 실제 날씨</option>
            {weatherPresets.map(preset => <option key={preset.value} value={preset.value}>{preset.label}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1.5 text-xs font-medium text-muted">시간대
          <select aria-label="테스트 시간대" value={actual ? 'actual' : period} disabled={actual} onChange={event => changeTime(periods[event.target.value] || time)} className={selectClass}>
            {actual && <option value="actual">실제 시간대</option>}
            <option value="dawn">새벽</option><option value="day">낮</option><option value="sunset">일몰</option><option value="evening">저녁</option><option value="night">밤</option>{period === 'custom' && <option value="custom">직접 지정</option>}
          </select>
        </label>
        <label className="flex flex-col gap-1.5 text-xs font-medium text-muted">시각
          <input aria-label="테스트 시각" type="time" value={time} disabled={actual} onChange={event => { if (event.target.value) changeTime(event.target.value) }} className={selectClass} />
        </label>
        <label className="flex flex-col gap-1.5 text-xs font-medium text-muted">달 기준 날짜
          <input aria-label="달 기준 날짜" type="date" min="1900-01-01" max="2050-12-31" value={date} disabled={actual}
            onChange={event => { if (lunarForDate(event.target.value)) setDate(event.target.value) }} className={selectClass} />
        </label>
        <label className="flex flex-col gap-1.5 text-xs font-medium text-muted">달 모양
          <select aria-label="테스트 달 모양" disabled={actual} value={moonDays.includes(lunar?.day) ? String(lunar.day) : 'custom'} className={selectClass}
            onChange={event => { const next = dateForLunarDay(date, Number(event.target.value)); if (next) { setDate(next); changeTime('22:00') } }}>
            <option value="1">삭 · 음력 1일</option><option value="4">초승달 · 4일</option><option value="8">상현달 · 8일</option><option value="15">보름달 · 15일</option><option value="22">하현달 · 22일</option><option value="27">그믐달 · 27일</option>
            {!moonDays.includes(lunar?.day) && <option value="custom">음력 {lunar?.day}일</option>}
          </select>
        </label>
        <a href={`/admin?${query}`} target="_blank" rel="noopener noreferrer" className="rounded-xl bg-apple-blue px-4 py-2.5 text-xs font-semibold text-white">새 탭에서 크게 보기</a>
      </div>
      <div className="mt-4 rounded-2xl bg-soft p-4">
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" className={buttonClass} disabled={actual} onClick={() => setPlaying(value => !value)}>{playing ? '일시정지' : '재생'}</button>
          <button type="button" className={buttonClass} onClick={startDay}>하루 전체 재생</button>
          <button type="button" className={buttonClass} disabled={actual} onClick={() => { setPlaying(false); setMinutes(value => (value + 15) % 1440) }}>+15분</button>
          <label className="flex items-center gap-2 text-xs text-muted">배속
            <select aria-label="시간 재생 배속" value={speed} onChange={event => setSpeed(Number(event.target.value))} className={selectClass}>
              <option value="60">60배 · 1초 = 1분</option><option value="300">300배 · 1초 = 5분</option><option value="900">900배 · 1초 = 15분</option><option value="3600">3600배 · 하루 24초</option>
            </select>
          </label>
          <output aria-label="시뮬레이션 시각" className="ml-auto text-sm font-bold tabular-nums">{actual ? '실제 시각' : `${time}${playing ? ' · 재생 중' : ' · 정지'}`}</output>
        </div>
        <input aria-label="하루 시간 이동" type="range" min="0" max="1439" step="1" value={Math.floor(minutes)} disabled={actual}
          onChange={event => { setPlaying(false); setMinutes(Number(event.target.value)) }} className="mt-4 block w-full accent-[#0071e3] disabled:opacity-40" />
        <div className="mt-1 flex justify-between text-[10px] tabular-nums text-muted"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div>
      </div>
      {!actual && lunar && <p className="mt-3 text-xs text-muted">{date} · 음력 {lunar.intercalation ? '윤' : ''}{lunar.month}월 {lunar.day}일 · 달 모양 선택 시 22시로 이동합니다.</p>}
      <p className="mt-3 text-xs leading-5 text-muted">동쪽(왼쪽) → 서쪽(오른쪽) · 테스트 일출 06:30 / 일몰 18:00 · 하루 끝에서 반복 재생됩니다. 카드·버튼·모달 색도 시간과 날씨에 맞춰 자동으로 변합니다. 선택은 미리보기에만 적용됩니다.</p>
    </header>
    <iframe ref={iframe} src="/admin?weatherPreview=current" onLoad={sendSettings} title="날씨 배경 미리보기" className="block h-[min(75vh,54rem)] min-h-[32rem] w-full border-0" />
  </section>
}
