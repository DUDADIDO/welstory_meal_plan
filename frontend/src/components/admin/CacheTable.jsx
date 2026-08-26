import { formatTime } from '../../utils/date'
import { formatBytes } from '../../utils/format'

export default function CacheTable({ caches }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[850px] border-collapse text-left text-sm">
        <thead className="border-b border-[var(--theme-border)] text-xs text-muted">
          <tr>
            {['날짜', '상태', '메뉴', '실제', '준비 중', '누락', '용량', '마지막 갱신'].map((label) => (
              <th className="px-5 py-3 font-semibold" key={label}>
                {label}
              </th>
            ))}
          </tr>
        </thead>

        <tbody className="divide-y divide-[var(--theme-border)]">
          {caches.map((cache) => (
            <tr
              key={cache.date}
              className="align-top transition-colors hover:bg-[var(--theme-hover)]"
            >
              <td className="px-5 py-4">
                <strong className="block">{cache.date}</strong>
                <small className="mt-1 block max-w-xs text-xs leading-5 text-muted">
                  {cache.message}
                </small>
              </td>

              <td className="px-5 py-4">
                <span
                  className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${
                    cache.complete
                      ? 'bg-emerald-500/10 text-emerald-600 ring-1 ring-emerald-500/20 dark:text-emerald-400'
                      : 'bg-amber-500/10 text-amber-600 ring-1 ring-amber-500/20 dark:text-amber-400'
                  }`}
                >
                  {cache.complete ? '캐시 완료' : '확인 필요'}
                </span>
              </td>

              <td className="px-5 py-4">{cache.menuCount}</td>
              <td className="px-5 py-4">{cache.readyImageCount}</td>
              <td className="px-5 py-4">{cache.placeholderImageCount}</td>
              <td className="px-5 py-4">{cache.missingImageCount}</td>

              <td className="px-5 py-4">
                {formatBytes(cache.diskBytes)}
              </td>

              <td className="px-5 py-4">
                {cache.lastUpdatedAt
                  ? formatTime(cache.lastUpdatedAt)
                  : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}