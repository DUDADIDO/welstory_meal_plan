import { useCallback, useMemo, useState } from 'react'
import SiteFooter from '../components/common/SiteFooter'
import MealNavigation from '../components/navigation/MealNavigation'
import EmptyState from '../components/meal/EmptyState'
import LoadingGrid from '../components/meal/LoadingGrid'
import MealCard from '../components/meal/MealCard'
import MealHero from '../components/meal/MealHero'
import MealLightbox from '../components/meal/MealLightbox'
import MealChat from '../components/meal/MealChat'
import MealNutrition from '../components/meal/MealNutrition'
import FloatingMealVote from '../components/meal/FloatingMealVote'
import WeatherBackdrop from '../components/common/WeatherBackdrop'
import { useMeals } from '../hooks/useMeals'
import { useRatings } from '../hooks/useRatings'
import { useReferencePhotos } from '../hooks/useReferencePhotos'
import { formatDate, todayInSeoul } from '../utils/date'
import StatusBadge from '../components/meal/StatusBadge'
import { useVisitorTracking } from '../hooks/useVisitorTracking'
import { useWeather } from '../hooks/useWeather'

export default function MainPage({ previewMode = false, weatherOverride = null }) {
  useVisitorTracking(!previewMode)
  const actualWeather = useWeather(!weatherOverride)
  const weather = weatherOverride || actualWeather
  const today = useMemo(todayInSeoul, [])
  const [date, setDate] = useState(today)
  const [openMeal, setOpenMeal] = useState(null)
  const [chatRoom, setChatRoom] = useState(null)
  const [nutritionMeal, setNutritionMeal] = useState(null)
  const { status, data, error, reload } = useMeals(date)
  const { ratings, rate, submitting } = useRatings(date)
  const hasMeals = data?.meals?.length > 0
  const references = useReferencePhotos(date, data?.date === date ? data?.meals : [])
  const closeLightbox = useCallback(() => setOpenMeal(null), [])
  const closeChat = useCallback(() => setChatRoom(null), [])
  const closeNutrition = useCallback(() => setNutritionMeal(null), [])

  return (
    <>
      <WeatherBackdrop weather={weather} />
      {previewMode ? <header className="bg-[#111] px-5 py-3 text-center text-xs font-semibold text-white">날씨 미리보기 · {weatherOverride ? '테스트 날씨' : '현재 실제 날씨'}</header>
        : <MealNavigation date={date} today={today} onChange={setDate} />}
      <main>
        <MealHero data={data} date={date} today={today} weather={weather} celebrate={!previewMode} />
        {status === 'loading' && !hasMeals && <LoadingGrid />}
        {hasMeals && (
          <section aria-label={`${formatDate(date)} 식단`} className="mx-auto max-w-7xl px-5 pb-24 sm:px-8">
            <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
              <p className="text-xs font-bold tracking-[0.14em] text-muted">
                오늘의 선택
              </p>

              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={data.status} />
                <span className="text-xs text-muted">
                  · {data.meals.length}가지 메뉴
                </span>
              </div>
            </div>
            {error && <p role="alert" className="mb-4 text-xs text-red-500">사진을 다시 확인하지 못했습니다. 잠시 후 자동으로 다시 확인합니다.</p>}
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {data.meals.map((meal, index) => (
                <MealCard key={meal.id} meal={meal} index={index} rating={ratings[meal.id]} ratingBusy={previewMode || submitting === meal.id}
                  onRate={(stars) => rate(meal.id, stars)} referencePhoto={references[meal.id]}
                  onNutrition={() => setNutritionMeal(meal)}
                  onOpen={() => setOpenMeal({ ...meal, imageUrl: meal.imageUrl || references[meal.id]?.imageUrl,
                    photoDate: meal.imageUrl ? null : references[meal.id]?.date, isReference: !meal.imageUrl })}
                  onChat={previewMode ? null : () => setChatRoom({ date, meal })} />
              ))}
            </div>
          </section>
        )}
        {!hasMeals && status !== 'loading' && <EmptyState data={data} error={error} onRetry={reload} />}
      </main>
      <SiteFooter data={data} weather={weather} />
      {!previewMode && !openMeal && !chatRoom && !nutritionMeal && <FloatingMealVote key={date} date={date} meals={data?.meals || []}
        canVote={date === today && data?.date === date && Boolean(hasMeals)} />}
      <MealLightbox meal={openMeal} date={date} onClose={closeLightbox} previewMode={previewMode} />
      {nutritionMeal && <MealNutrition meal={nutritionMeal} onClose={closeNutrition} />}
      {chatRoom && <MealChat key={`${chatRoom.date}:${chatRoom.meal.id}`} date={chatRoom.date} meal={chatRoom.meal} onClose={closeChat} />}
    </>
  )
}
