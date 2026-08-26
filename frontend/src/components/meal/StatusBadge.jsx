const statusStyle = {
  READY: 'bg-emerald-50 text-emerald-700 ring-emerald-600/15',
  WAITING: 'bg-amber-50 text-amber-700 ring-amber-600/15',
  UNAVAILABLE: 'bg-black/4 text-muted ring-black/8',
  ERROR: 'bg-red-50 text-red-700 ring-red-600/15',
}

const statusLabel = {
  READY: '식단 준비 완료',
  WAITING: '사진 확인 중',
  UNAVAILABLE: '식단 없음',
  ERROR: '연결 확인 필요',
}

export default function StatusBadge({ status }) {
  return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ring-1 ring-inset ${statusStyle[status] || statusStyle.UNAVAILABLE}`}>{statusLabel[status] || status}</span>
}
