import { formatDate, formatShortDate } from '../../utils/date'
import StatusBadge from './StatusBadge'

export default function MealHero({ data, date, today }) {
  const isToday = date === today
  return (
    <header id="top" className="mx-auto max-w-7xl px-5 pb-10 pt-14 text-center sm:px-8 sm:pb-14 sm:pt-20">
      <p className="mb-3 text-xs font-bold tracking-[0.2em] text-apple-blue">{isToday ? "TODAY'S LUNCH" : 'LUNCH ARCHIVE'}</p>
      <h1 className="text-balance text-[clamp(2.35rem,7vw,5.5rem)] font-bold leading-[0.98] tracking-[-0.055em]">{isToday ? '오늘, 뭐 먹을까요?' : `${formatShortDate(date)}의 식단`}</h1>
      <p className="mx-auto mt-5 max-w-xl text-sm leading-6 text-muted sm:text-base">{formatDate(date)} · {data?.restaurantName || '삼성전기 부산사업장'}</p>
      {data && <div className="mt-5"><StatusBadge status={data.status} /></div>}
    </header>
  )
}
