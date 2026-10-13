import { useEffect, useRef } from 'react'
import MealChat from './MealChat'
import { NutritionDetails } from './MealNutrition'

export default function MealLightbox({ meal, date, onClose, previewMode = false }) {
  const dialog = useRef(null)
  useEffect(() => {
    if (!meal) return undefined
    const panel = dialog.current
    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.showModal()
    return () => {
      panel.close()
      document.body.style.overflow = previousOverflow
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [meal])

  if (!meal) return null
  return <dialog ref={dialog} aria-modal="true" aria-labelledby="meal-detail-title"
    onCancel={event => { event.preventDefault(); onClose() }}
    onMouseDown={event => {
      if (event.target !== event.currentTarget) return
      const box = event.currentTarget.getBoundingClientRect()
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose()
    }}
    className="fixed inset-0 m-auto flex h-[min(56rem,calc(100dvh-2rem))] w-[min(76rem,calc(100vw-2rem))] max-w-none flex-col overflow-hidden rounded-3xl bg-surface p-0 text-ink shadow-2xl outline-none backdrop:bg-black/45 backdrop:backdrop-blur-sm">
    <header className="flex shrink-0 items-center justify-between gap-4 border-b border-line/50 px-5 py-3">
      <div className="min-w-0"><p className="text-xs text-muted">{meal.courseName}</p><h2 id="meal-detail-title" className="truncate text-lg font-bold">{meal.name}</h2></div>
      <button type="button" onClick={onClose} aria-label="확대 사진 닫기" className="grid size-9 shrink-0 place-items-center rounded-full bg-soft text-2xl text-muted hover:text-ink">×</button>
    </header>
    <div className="grid min-h-0 flex-1 overflow-y-auto md:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)] md:overflow-hidden">
      <div className="min-w-0 md:overflow-y-auto">
        <figure className="flex flex-col bg-black/90">
          <img src={meal.imageUrl} alt={`${meal.name} 식단 확대 사진`} className="max-h-[42dvh] min-h-0 w-full object-contain" />
          {meal.photoDate && <figcaption className="px-5 py-2 text-center text-xs text-white">{meal.photoDate} 식단 사진{meal.isReference && ' · 같은 메뉴의 과거 참고 사진입니다'}</figcaption>}
        </figure>
        <section aria-label="세부 영양 정보" className="py-2">
          <h3 className="px-5 pt-3 text-sm font-bold">세부 영양 정보</h3>
          <NutritionDetails meal={meal} />
        </section>
      </div>
      <div className="h-[32rem] min-h-0 border-t border-line/50 md:h-full md:border-l md:border-t-0">
        {previewMode ? <p className="p-5 text-sm text-muted">관리자 미리보기에서는 채팅을 사용할 수 없습니다.</p>
          : <MealChat key={`${date}:${meal.id}`} date={date} meal={meal} embedded />}
      </div>
    </div>
  </dialog>
}
