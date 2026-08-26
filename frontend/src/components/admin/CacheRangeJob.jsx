import { useMemo, useState } from 'react'
import PrimaryButton from '../common/PrimaryButton'
import { moveDate, todayInSeoul } from '../../utils/date'

const activeStatuses = new Set(['QUEUED', 'RUNNING', 'WAITING'])
const statusLabel = {
  QUEUED: '시작 대기', RUNNING: '수집 중', WAITING: '다음 호출 대기',
  COMPLETED: '완료', CANCELLED: '취소됨', FAILED: '실패',
}

export default function CacheRangeJob({ job, busy, onStart, onCancel }) {
  const today = useMemo(todayInSeoul, [])
  const [startDate, setStartDate] = useState(moveDate(today, -7))
  const [endDate, setEndDate] = useState(today)
  const [forceExisting, setForceExisting] = useState(false)
  const active = job && activeStatuses.has(job.status)

  async function submit(event) {
    event.preventDefault()
    await onStart(startDate, endDate, forceExisting)
  }

  return (
    <section className="mt-8 rounded-[1.5rem] bg-surface p-6 shadow-sm ring-1 ring-black/5">
      <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
        <div>
          <h2 className="text-lg font-bold">날짜 범위 캐시</h2>
          <p className="mt-1 text-xs leading-5 text-muted">미캐시 날짜를 순차 처리하며 실제 웰스토리 호출 사이에는 30초 간격을 둡니다.</p>
        </div>
        <form onSubmit={submit} className="flex flex-wrap items-end gap-2">
          <DateField label="시작일" value={startDate} max={today} onChange={setStartDate} disabled={active} />
          <DateField label="종료일" value={endDate} min={startDate} max={today} onChange={setEndDate} disabled={active} />
          <label className="flex h-10 items-center gap-2 rounded-xl bg-[var(--theme-subtle)] px-3 text-xs font-semibold text-muted">
            <input type="checkbox" checked={forceExisting} onChange={(event) => setForceExisting(event.target.checked)} disabled={active} />
            완료 캐시도 다시 확인
          </label>
          <PrimaryButton type="submit" disabled={busy || active || !startDate || !endDate}>범위 수집 시작</PrimaryButton>
          {active && <button type="button" onClick={onCancel} disabled={busy} className="rounded-full bg-red-50 px-4 py-2.5 text-sm font-semibold text-red-600 hover:bg-red-100 disabled:opacity-40">중지</button>}
        </form>
      </div>

      {job && (
        <div className="mt-6 rounded-2xl bg-[var(--theme-subtle)] p-5">
          <div className="flex items-center justify-between gap-4 text-sm">
            <div><strong>{statusLabel[job.status] || job.status}</strong><span className="ml-2 text-muted">{job.processed} / {job.total}일</span></div>
            <strong className="text-apple-blue">{job.progressPercent}%</strong>
          </div>
          <div className="mt-3 h-2 overflow-hidden rounded-full bg-[var(--theme-subtle)]"><div className="h-full rounded-full bg-apple-blue transition-[width] duration-500" style={{ width: `${job.progressPercent}%` }} /></div>
          <div className="mt-4 grid gap-2 text-xs text-muted sm:grid-cols-2 lg:grid-cols-4">
            <span>현재: <b className="text-ink">{job.currentDate || '—'}</b></span>
            <span>성공: <b className="text-ink">{job.succeeded}</b></span>
            <span>건너뜀: <b className="text-ink">{job.skipped}</b></span>
            <span>실패: <b className={job.failed ? 'text-red-600' : 'text-ink'}>{job.failed}</b></span>
          </div>
          {job.nextAttemptAt && <p className="mt-3 text-xs font-medium text-amber-700">다음 호출: {new Date(job.nextAttemptAt).toLocaleTimeString('ko-KR')}</p>}
          {job.lastError && <p className="mt-3 text-xs text-red-600">{job.lastError}</p>}
        </div>
      )}
    </section>
  )
}

function DateField({ label, value, onChange, ...props }) {
  return (
    <label className="grid gap-1 text-xs font-semibold text-muted">
      {label}
      <input type="date" value={value} onChange={(event) => onChange(event.target.value)} className="h-10 rounded-xl border border-[var(--theme-border)] bg-surface px-3 text-sm font-medium text-ink outline-none focus:border-apple-blue" {...props} />
    </label>
  )
}
