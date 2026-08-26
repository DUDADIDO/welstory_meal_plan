import AdminMetric from '../components/admin/AdminMetric'
import CacheCalendar from '../components/admin/CacheCalendar'
import CacheRangeJob from '../components/admin/CacheRangeJob'
import ServerLogs from '../components/admin/ServerLogs'
import PrimaryButton from '../components/common/PrimaryButton'
import { useAdminStatus } from '../hooks/useAdminStatus'
import { todayInSeoul } from '../utils/date'
import { formatBytes } from '../utils/format'

export default function AdminPage() {
  const { data, error, refreshing, jobBusy, refreshToday, startCacheJob, cancelCacheJob } = useAdminStatus()
  const todayCache = data?.caches?.find((cache) => cache.date === todayInSeoul())
  const totalBytes = data ? data.caches.reduce((sum, cache) => sum + cache.diskBytes, data.ratings.diskBytes) : 0

  return (
    <main className="mx-auto max-w-7xl px-5 py-12 sm:px-8 sm:py-16">
      <header className="flex flex-col justify-between gap-6 sm:flex-row sm:items-end">
        <div>
          <p className="text-xs font-bold tracking-[0.18em] text-apple-blue">SYSTEM STATUS</p>
          <h1 className="mt-2 text-4xl font-bold tracking-[-0.04em] sm:text-5xl">운영 콘솔</h1>
          <p className="mt-3 text-sm text-muted">캐시, 식단 수집, 준비 중 이미지 상태를 한눈에 확인합니다.</p>
        </div>
        <PrimaryButton onClick={refreshToday} disabled={refreshing}>{refreshing ? '확인 중…' : '지금 확인'}</PrimaryButton>
      </header>

      {error && <p className="mt-8 rounded-2xl bg-red-50 px-5 py-4 text-sm font-medium text-red-700 ring-1 ring-red-600/10">{error.message}</p>}

      {data && (
        <>
          <section
            aria-label="현재 상태"
            className="mt-10 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-8"
          >
            <AdminMetric
              label="오늘 방문자"
              value={`${data.visitors?.dailyVisitors || 0}명`}
            />

            <AdminMetric
              label="이번 달 방문자"
              value={`${data.visitors?.monthlyVisitors || 0}명`}
            />
            <AdminMetric label="오늘 캐시" value={todayCache?.complete ? '완료' : '대기'} tone={todayCache?.complete ? 'good' : 'waiting'} />
            <AdminMetric label="실제 이미지" value={`${todayCache?.readyImageCount || 0}개`} />
            <AdminMetric label="준비 중 이미지" value={`${todayCache?.placeholderImageCount || 0}개`} tone={todayCache?.placeholderImageCount ? 'warning' : undefined} />
            <AdminMetric label="누락 이미지" value={`${todayCache?.missingImageCount || 0}개`} />
            <AdminMetric label="누적 별점" value={`${data.ratings.voteCount}개`} />
            <AdminMetric label="캐시 사용량" value={formatBytes(totalBytes)} />
          </section>

          <section className="mt-8 overflow-hidden rounded-[1.5rem] bg-surface shadow-sm ring-1 ring-[var(--theme-border)]">
            <div className="border-b border-black/7 px-5 py-5 sm:px-6">
              <h2 className="text-lg font-bold">날짜별 캐시</h2>
              <p className="mt-1 text-xs leading-5 text-muted">{data.pollingSchedule} · 완료된 날짜는 웰스토리에 다시 요청하지 않습니다.</p>
            </div>
            <CacheCalendar caches={data.caches} />
          </section>

          <CacheRangeJob job={data.cacheJob} busy={jobBusy} onStart={startCacheJob} onCancel={cancelCacheJob} />

          <ServerLogs />

          <section className="mt-8 rounded-[1.5rem] bg-surface p-6 shadow-sm ring-1 ring-[var(--theme-border)]">
            <h2 className="text-lg font-bold">서버 설정</h2>
            <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
              <ConfigItem label="식당" value={`${data.restaurantName} · ${data.restaurantCode}`} />
              <ConfigItem label="웰스토리 계정" value={data.welstoryCredentialsConfigured ? '설정됨' : '미설정'} />
              <ConfigItem label="어드민 계정" value={data.adminCredentialsConfigured ? '설정됨' : '미설정'} />
              <ConfigItem label="캐시 경로" value={data.cacheDirectory} />
            </dl>
          </section>
        </>
      )}
    </main>
  )
}

function ConfigItem({ label, value }) {
  return <div className="rounded-xl bg-[var(--theme-subtle)] px-4 py-3"><dt className="text-xs font-semibold text-muted">{label}</dt><dd className="mt-1 break-all font-medium">{value}</dd></div>
}
