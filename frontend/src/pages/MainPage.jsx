import { useCallback, useMemo, useState } from 'react'
import SiteFooter from '../components/common/SiteFooter'
import MealNavigation from '../components/navigation/MealNavigation'
import EmptyState from '../components/meal/EmptyState'
import LoadingGrid from '../components/meal/LoadingGrid'
import MealCard from '../components/meal/MealCard'
import MealHero from '../components/meal/MealHero'
import MealLightbox from '../components/meal/MealLightbox'
import { useMeals } from '../hooks/useMeals'
import { useRatings } from '../hooks/useRatings'
import { formatDate, todayInSeoul } from '../utils/date'

export default function MainPage() {
  const today = useMemo(todayInSeoul, [])
  const [date, setDate] = useState(today)
  const [openMeal, setOpenMeal] = useState(null)
  const { status, data, error, reload } = useMeals(date)
  const { ratings, rate, submitting } = useRatings(date)
  const hasMeals = data?.meals?.length > 0
  const closeLightbox = useCallback(() => setOpenMeal(null), [])

  return (
    <>
      <MealNavigation date={date} today={today} onChange={setDate} />
      <main>
        <MealHero data={data} date={date} today={today} />
        {status === 'loading' && <LoadingGrid />}
        {hasMeals && (
          <section aria-label={`${formatDate(date)} 식단`} className="mx-auto max-w-7xl px-5 pb-24 sm:px-8">
            <div className="mb-5 flex items-end justify-between">
              <p className="text-xs font-bold tracking-[0.14em] text-muted">오늘의 선택</p>
              <p className="text-xs text-muted">{data.meals.length}가지 메뉴</p>
            </div>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {data.meals.map((meal, index) => (
                <MealCard key={meal.id} meal={meal} index={index} rating={ratings[meal.id]} ratingBusy={submitting === meal.id} onRate={(stars) => rate(meal.id, stars)} onOpen={() => setOpenMeal(meal)} />
              ))}
            </div>
          </section>
        )}
        {!hasMeals && status !== 'loading' && <EmptyState data={data} error={error} onRetry={reload} />}
      </main>
      <SiteFooter data={data} />
      <MealLightbox meal={openMeal} onClose={closeLightbox} />
    </>
  )
}
