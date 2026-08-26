export default function ZoomIcon({ className = 'size-4' }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className={className} fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="1.7">
      <circle cx="8.5" cy="8.5" r="4.75" />
      <path d="m12 12 4 4M8.5 6.5v4M6.5 8.5h4" />
    </svg>
  )
}
