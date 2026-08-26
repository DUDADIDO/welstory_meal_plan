const toneStyle = {
  good: 'text-emerald-600',
  waiting: 'text-amber-600',
  warning: 'text-red-600',
}

export default function AdminMetric({ label, value, tone }) {
  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-black/5">
      <span className="text-xs font-semibold text-muted">{label}</span>
      <strong className={`mt-2 block text-2xl tracking-tight ${toneStyle[tone] || 'text-ink'}`}>{value}</strong>
    </div>
  )
}
