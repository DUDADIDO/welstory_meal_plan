import { useEffect } from 'react'

export default function MealLightbox({ meal, onClose }) {
  useEffect(() => {
    if (!meal) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKeyDown = (event) => { if (event.key === 'Escape') onClose() }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [meal, onClose])

  if (!meal) return null
  return (
    <div role="dialog" aria-modal="true" aria-label={`${meal.name} 식단 사진`} onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }} className="fixed inset-0 z-50 grid place-items-center bg-black/78 p-4 backdrop-blur-xl sm:p-8">
      <button type="button" onClick={onClose} aria-label="확대 사진 닫기" className="absolute right-5 top-5 grid size-11 place-items-center rounded-full bg-white/14 text-2xl font-light text-white backdrop-blur-lg transition hover:bg-white/22">×</button>
      <figure className="max-h-[88dvh] max-w-5xl overflow-hidden rounded-[1.8rem] bg-[#1c1c1e] shadow-2xl ring-1 ring-white/15">
        <img src={meal.imageUrl} alt={`${meal.name} 식단 확대 사진`} className="max-h-[78dvh] w-full object-contain" />
        <figcaption className="flex flex-col gap-1 px-5 py-4 text-white sm:px-7">
          <span className="text-[0.68rem] font-bold tracking-[0.12em] text-white/45">{meal.courseName}</span>
          <strong className="text-lg">{meal.name}</strong>
        </figcaption>
      </figure>
    </div>
  )
}
