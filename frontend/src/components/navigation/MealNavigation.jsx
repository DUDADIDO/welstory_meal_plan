import Brand from '../common/Brand'
import ChevronIcon from '../../assets/icons/ChevronIcon'
import DatePicker from '../calendar/DatePicker'
import { formatDate, moveDate } from '../../utils/date'
import ThemeToggle from '../common/ThemeToggle'

const arrowButton =
  'grid size-9 place-items-center rounded-full border border-[var(--theme-border)] bg-surface text-ink shadow-sm transition hover:bg-[var(--theme-hover)] disabled:opacity-25'

export default function MealNavigation({ date, today, onChange }) {
  return (
    <>
      <header className="border-b border-[var(--theme-border)] bg-canvas/90 backdrop-blur-2xl">
      <div className="mx-auto flex h-12 max-w-7xl items-center justify-between px-5 sm:px-8">
        <Brand />

        <div className="flex items-center gap-3">
          <span className="hidden text-[0.68rem] font-semibold tracking-[0.12em] text-muted sm:block">
            SAMSUNG ELECTRO-MECHANICS · BUSAN
          </span>

          <ThemeToggle />
        </div>
      </div>
      </header>
      <nav
        aria-label="날짜 선택"
        className="
          sticky top-0 z-30
          border-b border-[var(--theme-border)]
          bg-[color:var(--theme-surface)]/90
          backdrop-blur-2xl
        "
      >
        <div className="mx-auto flex min-h-15 max-w-7xl items-center justify-between gap-4 px-5 py-2 sm:px-8">
          <div className="min-w-0">
            <p className="text-[0.65rem] font-bold tracking-[0.13em] text-apple-blue">점심 식단</p>
            <p className="truncate text-sm font-semibold sm:text-base">{formatDate(date)}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <button type="button" className={arrowButton} onClick={() => onChange(moveDate(date, -1))} aria-label="이전 날짜"><ChevronIcon direction="left" /></button>
            <DatePicker date={date} today={today} onChange={onChange} />
            <button type="button" className={arrowButton} onClick={() => onChange(moveDate(date, 1))} aria-label="다음 날짜"><ChevronIcon /></button>
          </div>
        </div>
      </nav>
    </>
  )
}
