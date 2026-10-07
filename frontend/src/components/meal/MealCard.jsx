import MarkIcon from '../../assets/icons/MarkIcon'
import StarRating from './StarRating'
import { formatShortDate } from '../../utils/date'

export default function MealCard({
  meal,
  index,
  rating,
  ratingBusy,
  onRate,
  onOpen,
  onChat,
  referencePhoto,
}) {
  const imageUrl = meal.imageUrl || referencePhoto?.imageUrl
  const isReference = !meal.imageUrl && Boolean(referencePhoto?.imageUrl)
  return (
    <article
      className="
        group
        flex h-full flex-col
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
      <div className="relative aspect-[4/3] shrink-0 overflow-hidden bg-soft">
        {imageUrl ? (
          <button
            type="button"
            onClick={onOpen}
            aria-label={`${meal.name}${isReference ? ' 지난 메뉴 참고' : ''} 사진 크게 보기`}
            className="block size-full overflow-hidden text-left"
          >
            <img
              src={imageUrl}
              alt={`${meal.name}${isReference ? ` ${referencePhoto.date} 참고` : ' 식단'} 사진`}
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
        {isReference && <div className="pointer-events-none absolute bottom-3 left-3 right-3">
          <span className="inline-flex rounded-full bg-black/65 px-3 py-1.5 text-[0.65rem] font-semibold text-white backdrop-blur-sm">{formatShortDate(referencePhoto.date)} 참고 사진 · 해당 날짜 사진 준비 중</span>
        </div>}
      </div>

      <div className="flex flex-1 flex-col p-5 sm:p-6">
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

        {meal.calorie && (
          <p className="mt-3 inline-flex self-start items-center rounded-full bg-orange-500/10 px-3 py-1 text-xs font-semibold text-orange-600">
            총 칼로리 · {meal.calorie}
          </p>
        )}

        <div className="mt-auto pt-5">
          <StarRating
            mealName={meal.name}
            rating={rating}
            disabled={ratingBusy}
            onRate={onRate}
            action={(
              <button
                type="button"
                onClick={onChat}
                aria-label={`${meal.name} 채팅방 열기`}
                title="식단 이야기하기"
                className="grid size-6 shrink-0 place-items-center rounded-md text-apple-blue transition hover:bg-apple-blue/10 focus-visible:outline-2 focus-visible:outline-apple-blue"
              >
                <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-1 1v-9.5a8.5 8.5 0 0 1 17 0Z" />
                  <path d="M8 11h8M8 15h5" />
                </svg>
              </button>
            )}
          />
        </div>
      </div>
    </article>
  )
}
