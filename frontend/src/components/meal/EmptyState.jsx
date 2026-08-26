import PrimaryButton from '../common/PrimaryButton'

export default function EmptyState({ data, error, onRetry }) {
  const title = error
    ? '잠시 연결이 매끄럽지 않아요.'
    : data?.status === 'UNAVAILABLE' ? '이 날짜의 식단이 없습니다.' : '웰스토리가 메뉴를 준비하고 있어요.'
  const message = error?.message || data?.message || '식단 사진은 늦어도 오전 10시 40분경 등록되는 편이에요.'

  return (
    <section className="mx-auto mb-24 max-w-2xl rounded-[2rem] bg-white px-6 py-16 text-center shadow-[0_2px_24px_rgb(0_0_0/0.05)] ring-1 ring-black/5 sm:px-12">
      <span aria-hidden="true" className="text-4xl">◌</span>
      <h2 className="mt-5 text-2xl font-bold tracking-tight">{title}</h2>
      <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-muted">{message}</p>
      {(error || data?.status === 'WAITING') && <PrimaryButton className="mt-7" onClick={onRetry}>다시 확인</PrimaryButton>}
    </section>
  )
}
