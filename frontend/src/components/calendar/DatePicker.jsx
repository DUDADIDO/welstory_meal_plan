import { useEffect, useRef, useState } from 'react'
import CalendarIcon from '../../assets/icons/CalendarIcon'
import { formatShortDate } from '../../utils/date'
import Calendar from './Calendar'

export default function DatePicker({ date, today, onChange }) {
  const [open, setOpen] = useState(false)
  const pickerRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (event) => { if (!pickerRef.current?.contains(event.target)) setOpen(false) }
    const onKeyDown = (event) => { if (event.key === 'Escape') setOpen(false) }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  function selectDate(nextDate) {
    onChange(nextDate)
    setOpen(false)
  }

  return (
    <div className="relative" ref={pickerRef}>
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="
        flex h-9 w-[6.75rem] items-center justify-center gap-1.5
        rounded-full
        bg-[var(--theme-subtle)]
        px-2
        text-xs font-semibold
        text-ink
        transition
        hover:bg-[var(--theme-hover)]
        sm:w-[7.25rem]
        sm:px-3
        "
      >
        <CalendarIcon className="size-4 shrink-0" />

        <span className="min-w-0 text-center">
          {date === today ? '오늘' : formatShortDate(date)}
        </span>
      </button>
      {open && <Calendar date={date} today={today} onSelect={selectDate} />}
    </div>
  )
}
