import { useMemo, useState } from 'react'
import { formatBytes } from '../../utils/format'
import { formatTime } from '../../utils/date'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function monthString(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function shiftMonth(value, amount) {
  const [year, month] = value.split('-').map(Number)
  const date = new Date(year, month - 1 + amount, 1)

  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export default function CacheCalendar({ caches }) {
  const [month, setMonth] = useState(monthString())
  const [selected, setSelected] = useState(null)

  const cacheMap = useMemo(
    () => Object.fromEntries(caches.map((cache) => [cache.date, cache])),
    [caches],
  )

  const [year, monthNumber] = month.split('-').map(Number)
  const firstDay = new Date(year, monthNumber - 1, 1).getDay()
  const dayCount = new Date(year, monthNumber, 0).getDate()

  const cells = [
    ...Array(firstDay).fill(null),
    ...Array.from({ length: dayCount }, (_, index) => index + 1),
  ]

  const selectedCache = selected ? cacheMap[selected] : null

  return (
    <div className="p-5 sm:p-6">
      <div className="mx-auto max-w-[22rem]">
        <div className="mb-4 flex items-center justify-between px-1">
          <button
            type="button"
            onClick={() => setMonth((value) => shiftMonth(value, -1))}
            className="grid size-8 place-items-center rounded-full text-lg text-ink transition hover:bg-[var(--theme-hover)]"
            aria-label="이전 달"
          >
            ‹
          </button>

          <strong className="text-[0.95rem]">
            {year}년 {monthNumber}월
          </strong>

          <button
            type="button"
            onClick={() => setMonth((value) => shiftMonth(value, 1))}
            className="grid size-8 place-items-center rounded-full text-lg text-ink transition hover:bg-[var(--theme-hover)]"
            aria-label="다음 달"
          >
            ›
          </button>
        </div>

        <div className="grid grid-cols-7 text-center text-[0.68rem] font-semibold text-muted">
          {WEEKDAYS.map((day) => (
            <span key={day} className="py-2">
              {day}
            </span>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-y-1">
          {cells.map((day, index) => {
            if (!day) {
              return <span key={`blank-${index}`} />
            }

            const isoDate = `${month}-${String(day).padStart(2, '0')}`
            const cache = cacheMap[isoDate]
            const selectedDay = isoDate === selected

            let stateClass = 'text-muted hover:bg-[var(--theme-hover)]'

            if (cache?.complete) {
              stateClass =
                'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-600/15 hover:bg-emerald-100'
            } else if (cache) {
              stateClass =
                'bg-amber-50 text-amber-700 ring-1 ring-amber-600/15 hover:bg-amber-100'
            }

            if (selectedDay) {
              stateClass =
                'bg-apple-blue text-white ring-0 hover:bg-apple-blue'
            }

            return (
              <button
                key={isoDate}
                type="button"
                onClick={() => setSelected(isoDate)}
                aria-pressed={selectedDay}
                aria-label={`${year}년 ${monthNumber}월 ${day}일`}
                className={`mx-auto flex size-9 flex-col items-center justify-center rounded-full text-xs font-semibold transition ${stateClass}`}
              >
                <span>{day}</span>

                {cache && !selectedDay && (
                  <span
                    className={`mt-0.5 size-1 rounded-full ${
                      cache.complete
                        ? 'bg-emerald-500'
                        : 'bg-amber-500'
                    }`}
                  />
                )}
              </button>
            )
          })}
        </div>

        <div className="mt-4 flex flex-wrap justify-center gap-4 text-[0.68rem] text-muted">
          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-emerald-500" />
            캐시 완료
          </span>

          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-amber-500" />
            확인 필요
          </span>

          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-black/15" />
            캐시 없음
          </span>
        </div>
      </div>

      {selected && (
        <div className="mt-6 rounded-2xl bg-[var(--theme-subtle)] p-5">
          <div className="flex items-center justify-between">
            <strong>{selected}</strong>

            <button
              type="button"
              onClick={() => setSelected(null)}
              className="text-xs font-semibold text-muted hover:text-ink"
            >
              닫기
            </button>
          </div>

          {!selectedCache ? (
            <p className="mt-3 text-sm text-muted">
              캐시된 정보가 없습니다.
            </p>
          ) : (
            <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
              <Info label="상태">
                {selectedCache.complete
                  ? '캐시 완료'
                  : '확인 필요'}
              </Info>

              <Info label="메뉴">
                {selectedCache.menuCount}개
              </Info>

              <Info label="실제 이미지">
                {selectedCache.readyImageCount}개
              </Info>

              <Info label="준비 중">
                {selectedCache.placeholderImageCount}개
              </Info>

              <Info label="누락">
                {selectedCache.missingImageCount}개
              </Info>

              <Info label="용량">
                {formatBytes(selectedCache.diskBytes)}
              </Info>

              <Info label="마지막 갱신">
                {selectedCache.lastUpdatedAt
                  ? formatTime(selectedCache.lastUpdatedAt)
                  : '—'}
              </Info>

              <Info label="메시지">
                {selectedCache.message || '—'}
              </Info>
            </dl>
          )}
        </div>
      )}
    </div>
  )
}

function Info({ label, children }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-muted">
        {label}
      </dt>

      <dd className="mt-1 font-medium">
        {children}
      </dd>
    </div>
  )
}