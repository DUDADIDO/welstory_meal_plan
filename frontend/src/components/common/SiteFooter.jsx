import MarkIcon from '../../assets/icons/MarkIcon'
import { formatTime } from '../../utils/date'

export default function SiteFooter({ data }) {
  const updated = formatTime(data?.lastUpdatedAt)

  return (
    <footer className="border-t border-[var(--theme-border)] bg-surface">
      <div className="mx-auto max-w-7xl px-5 py-10 text-xs leading-5 text-muted sm:px-8">
        <div className="mb-3 flex items-center gap-2 text-ink">
          <MarkIcon className="size-5" />
          <strong>오늘 뭐먹지</strong>
        </div>

        <p>삼성 웰스토리에서 제공하는 식단 정보를 보기 편하게 정리합니다.</p>

        <p className="mt-2 text-[0.68rem] text-muted">
          {updated
            ? `마지막 업데이트 ${updated}`
            : '식단 업데이트를 기다리는 중입니다.'}
          {' · '}
          이미지와 메뉴 정보의 권리는 제공처에 있습니다.
        </p>
      </div>
    </footer>
  )
}