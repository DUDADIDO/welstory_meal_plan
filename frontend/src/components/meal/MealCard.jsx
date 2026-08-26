import MarkIcon from '../../assets/icons/MarkIcon'
import StarRating from './StarRating'

export default function MealCard({
  meal,
  index,
  rating,
  ratingBusy,
  onRate,
  onOpen,
}) {
  return (
    <article
      className="
        group
        overflow-hidden
        rounded-[1.75rem]
        bg-surface
        shadow-[0_2px_20px_rgb(0_0_0/0.055)]
        ring-1 ring-[var(--theme-border)]
        transition duration-300
        hover:-translate-y-1
        hover:shadow-apple
      "
    >
      <div className="relative aspect-[4/3] overflow-hidden bg-soft">
        {meal.imageUrl ? (
          <button
            type="button"
            onClick={onOpen}
            aria-label={`${meal.name} 사진 크게 보기`}
            className="block size-full overflow-hidden text-left"
          >
            <img
              src={meal.imageUrl}
              alt={`${meal.name} 식단 사진`}
              loading={index < 3 ? 'eager' : 'lazy'}
              decoding="async"
              className="size-full object-cover transition duration-500 group-hover:scale-[1.025]"
            />
          </button>
        ) : (
          <div
            className="flex size-full flex-col items-center justify-center gap-3 text-muted opacity-50"
            role="img"
            aria-label="식단 사진 준비 중"
          >
            <MarkIcon className="size-10" />
            <span className="text-xs font-semibold">
              사진 준비 중
            </span>
          </div>
        )}
      </div>

      <div className="p-5 sm:p-6">
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="text-[0.68rem] font-bold tracking-[0.13em] text-apple-blue">
            {meal.courseName || '오늘의 메뉴'}
          </p>

          <span className="text-xs font-semibold text-muted opacity-50">
            {String(index + 1).padStart(2, '0')}
          </span>
        </div>

        <h2 className="text-xl font-bold leading-tight tracking-[-0.025em]">
          {meal.name}
        </h2>

        {meal.description && (
          <p className="mt-2 line-clamp-2 text-sm leading-5 text-muted">
            {meal.description}
          </p>
        )}

        <div className="mt-5">
          <StarRating
            mealName={meal.name}
            rating={rating}
            disabled={ratingBusy}
            onRate={onRate}
          />
        </div>
      </div>
    </article>
  )
}