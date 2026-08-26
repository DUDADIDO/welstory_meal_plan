export default function CalendarIcon({ className = 'size-5' }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className={className} fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5">
      <rect x="3" y="4.5" width="14" height="12.5" rx="2" />
      <path d="M6.5 2.5v4M13.5 2.5v4M3 8h14" />
    </svg>
  )
}
