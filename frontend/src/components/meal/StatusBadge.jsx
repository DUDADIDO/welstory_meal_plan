const statusStyle = {
  READY: 'text-emerald-600',
  WAITING: 'text-amber-600',
  UNAVAILABLE: 'text-black/40',
  ERROR: 'text-red-600',
}

const statusDotStyle = {
  READY: 'bg-emerald-500',
  WAITING: 'bg-amber-500',
  UNAVAILABLE: 'bg-black/30',
  ERROR: 'bg-red-500',
}

const statusLabel = {
  READY: '식단 준비 완료',
  WAITING: '사진 확인 중',
  UNAVAILABLE: '식단 없음',
  ERROR: '연결 확인 필요',
}

export default function StatusBadge({ status }) {
  const textStyle = statusStyle[status] || statusStyle.UNAVAILABLE
  const dotStyle = statusDotStyle[status] || statusDotStyle.UNAVAILABLE

  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-bold ${textStyle}`}>
      <span className={`size-1.5 rounded-full ${dotStyle}`} />
      {statusLabel[status] || status}
    </span>
  )
}