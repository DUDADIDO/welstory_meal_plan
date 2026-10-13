import { useEffect, useRef } from 'react'

export function NutritionDetails({ meal }) {
  const nutrition = meal.nutrition || {}
  const rows = [
    ['메인 메뉴 칼로리', nutrition.kcal],
    ['코스 전체 칼로리', nutrition.sumKcal || meal.calorie?.replace(/\s*kcal$/i, '')],
    ['합계 단백질', nutrition.sumProtein],
    ['합계 지방', nutrition.sumFat],
    ['합계 나트륨', nutrition.sumNa],
  ]

  return <>
    <dl className="divide-y divide-line/50 px-5">
      {rows.map(([label, value], index) => <div key={label} className="flex items-center justify-between gap-4 py-3.5">
        <dt className="text-sm text-muted">{label}</dt><dd className={`text-right text-base font-semibold tabular-nums ${index === 1 ? 'text-orange-600' : ''}`}>{value ?? '—'}</dd>
      </div>)}
    </dl>
    <p className="border-t border-line/50 px-5 py-3 text-[0.65rem] leading-5 text-muted">웰스토리 제공 정보 · 제공되지 않은 항목은 —로 표시합니다.</p>
  </>
}

export default function MealNutrition({ meal, onClose }) {
  const dialog = useRef(null)
  useEffect(() => {
    const panel = dialog.current
    const previousFocus = document.activeElement
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.showModal()
    return () => {
      panel.close()
      document.body.style.overflow = overflow
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [])


  return <dialog ref={dialog} aria-modal="true" aria-labelledby="meal-nutrition-title"
    onCancel={event => { event.preventDefault(); onClose() }}
    onKeyDown={event => { if (event.key === 'Tab') { event.preventDefault(); dialog.current.querySelector('button').focus() } }}
    onMouseDown={event => {
      if (event.target !== event.currentTarget) return
      const box = event.currentTarget.getBoundingClientRect()
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose()
    }}
    className="fixed inset-0 m-auto w-[min(26rem,calc(100vw-2rem))] overflow-hidden rounded-3xl bg-surface p-0 text-ink shadow-2xl outline-none ring-1 ring-[var(--theme-border)] backdrop:bg-black/45 backdrop:backdrop-blur-sm">
    <header className="flex items-start justify-between gap-3 border-b border-line/50 px-5 py-4">
      <div className="min-w-0"><p className="text-xs text-muted">{meal.courseName}</p><h2 id="meal-nutrition-title" className="mt-1 text-base font-bold">영양 정보</h2><p className="mt-1 break-words text-xs text-muted">{meal.name}</p></div>
      <button type="button" onClick={onClose} aria-label="영양 정보 닫기" className="grid size-7 shrink-0 place-items-center rounded-full bg-soft text-lg text-muted">×</button>
    </header>
    <NutritionDetails meal={meal} />
  </dialog>
}
