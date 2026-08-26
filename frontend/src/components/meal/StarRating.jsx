export default function StarRating({ mealName, rating, disabled, onRate }) {
  const selected = rating?.myRating || 0
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 border-t border-black/7 pt-4">
      <div className="flex" role="group" aria-label={`${mealName} 별점`}>
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            type="button"
            key={star}
            disabled={disabled}
            aria-label={`${star}점`}
            aria-pressed={star === selected}
            onClick={() => onRate(star)}
            className={`px-0.5 text-[1.15rem] leading-none transition hover:scale-110 disabled:opacity-45 ${star <= selected ? 'text-[#ff9f0a]' : 'text-black/16 hover:text-[#ff9f0a]/70'}`}
          >★</button>
        ))}
      </div>
      <span className="text-[0.68rem] font-medium text-muted">{rating?.count ? `${Number(rating.average).toFixed(1)} · ${rating.count}명` : '첫 별점을 남겨보세요'}</span>
    </div>
  )
}
