import { useEffect, useState } from 'react'
import ChevronIcon from '../../assets/icons/ChevronIcon'
import { formatMonth, monthParts, moveMonth, WEEKDAYS } from '../../utils/date'

const navButton = 'grid size-8 place-items-center rounded-full text-ink transition hover:bg-[var(--theme-hover)] disabled:opacity-20'

export default function Calendar({ date, today, onSelect }) {
  const [viewMonth, setViewMonth] = useState(date.slice(0, 7))
  const { year, month } = monthParts(viewMonth)
  const daysInMonth = new Date(year, month, 0).getDate()
  const firstWeekday = new Date(year, month - 1, 1).getDay()
  const cells = Array.from({ length: 42 }, (_, index) => {
    const day = index - firstWeekday + 1
    return day > 0 && day <= daysInMonth ? day : null
  })

  useEffect(() => setViewMonth(date.slice(0, 7)), [date])

  return (
    <div role="dialog" aria-label="날짜 선택" className="
      absolute right-0 top-[calc(100%+0.65rem)] z-40
      w-[19rem]
      rounded-[1.6rem]
      border border-[var(--theme-border)]
      bg-surface
      p-4
      text-ink
      shadow-apple
    "
  >
      <div className="mb-3 flex items-center justify-between px-1">
        <strong className="text-[0.95rem]">{formatMonth(year, month)}</strong>
        <div className="flex gap-1">
          <button type="button" className={navButton} onClick={() => setViewMonth(moveMonth(viewMonth, -1))} aria-label="이전 달"><ChevronIcon direction="left" /></button>
          <button type="button" className={navButton} onClick={() => setViewMonth(moveMonth(viewMonth, 1))} aria-label="다음 달"><ChevronIcon /></button>
        </div>
      </div>
      <div className="grid grid-cols-7 text-center text-[0.68rem] font-semibold text-muted">
        {WEEKDAYS.map((weekday) => <span className="py-2" key={weekday}>{weekday}</span>)}
      </div>
      <div className="grid grid-cols-7 gap-y-1">
        {cells.map((day, index) => {
          if (day === null) return <span key={`blank-${index}`} />
          const isoDate = `${viewMonth}-${String(day).padStart(2, '0')}`
          const selected = isoDate === date
          const isToday = isoDate === today
          return (
            <button
              type="button"
              key={isoDate}
              aria-pressed={selected}
              aria-label={`${year}년 ${month}월 ${day}일${isToday ? ', 오늘' : ''}`}
              onClick={() => onSelect(isoDate)}
              className={`mx-auto grid size-9 place-items-center rounded-full text-sm transition disabled:text-black/20 ${selected ? 'bg-apple-blue font-semibold text-white' : 'hover:bg-[var(--theme-hover)]'} ${isToday && !selected ? 'font-bold text-apple-blue' : ''}`}
            >
              {day}
            </button>
          )
        })}
      </div>
      {date !== today && <button type="button" className="mt-3 w-full rounded-xl py-2 text-sm font-semibold text-apple-blue hover:bg-blue-50" onClick={() => onSelect(today)}>오늘로 돌아가기</button>}
    </div>
  )
}
