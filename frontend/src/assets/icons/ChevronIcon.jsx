export default function ChevronIcon({ direction = 'right', className = 'size-5' }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className={className} fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.8">
      <path d={direction === 'left' ? 'm12.5 4-6 6 6 6' : 'm7.5 4 6 6-6 6'} />
    </svg>
  )
}
