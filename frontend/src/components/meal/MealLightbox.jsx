import { useEffect } from 'react'

export default function MealLightbox({ meal, onClose }) {
  useEffect(() => {
    if (!meal) return undefined

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', onKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [meal, onClose])

  if (!meal) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`${meal.name} 식단 사진`}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      className="fixed inset-0 z-50 grid place-items-center bg-black/45 p-4 backdrop-blur-[2px] sm:p-8"
    >
      <button
        type="button"
        onClick={onClose}
        aria-label="확대 사진 닫기"
        className="absolute right-5 top-5 grid size-11 place-items-center rounded-full bg-white/14 text-2xl font-light text-white backdrop-blur-sm transition hover:bg-white/22"
      >
        ×
      </button>

      <figure className="max-h-[92dvh] max-w-5xl overflow-hidden rounded-[1.8rem] bg-transparent shadow-2xl ring-1 ring-white/15">
        <img
          src={meal.imageUrl}
          alt={`${meal.name} 식단 확대 사진`}
          className="max-h-[88dvh] w-full object-contain"
        />
      </figure>
    </div>
  )
}