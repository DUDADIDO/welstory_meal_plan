import { useCallback, useEffect, useMemo, useState } from 'react'

const KOREA_TIMEZONE = 'Asia/Seoul'

function toLocalDateString(date) {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: KOREA_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

function moveDate(isoDate, amount) {
  const date = new Date(`${isoDate}T12:00:00+09:00`)
  date.setDate(date.getDate() + amount)
  return toLocalDateString(date)
}

function formatDate(isoDate) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: KOREA_TIMEZONE,
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date(`${isoDate}T12:00:00+09:00`))
}

function formatTime(instant) {
  if (!instant) return null
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: KOREA_TIMEZONE,
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(instant))
}

function useMeals(date) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [reloadKey, setReloadKey] = useState(0)

  const reload = useCallback(() => setReloadKey((key) => key + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    let pollTimer

    async function load() {
      setState((previous) => ({ ...previous, status: previous.data ? 'refreshing' : 'loading', error: null }))
      try {
        const response = await fetch(`/api/meals?date=${date}`, {
          signal: controller.signal,
          headers: { Accept: 'application/json' },
        })
        if (!response.ok) throw new Error(`식단을 불러오지 못했습니다. (${response.status})`)
        const data = await response.json()
        setState({ status: 'success', data, error: null })
        if (data.status === 'WAITING') {
          pollTimer = window.setTimeout(load, 60_000)
        }
      } catch (error) {
        if (error.name !== 'AbortError') {
          setState((previous) => ({ ...previous, status: 'error', error }))
        }
      }
    }

    load()
    return () => {
      controller.abort()
      window.clearTimeout(pollTimer)
    }
  }, [date, reloadKey])

  return { ...state, reload }
}

function Mark() {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32" className="mark">
      <path d="M8 5v8.5c0 3 2.2 5.5 5.2 6V27h5.6v-7.5c3-0.5 5.2-3 5.2-6V5h-3.5v8.5c0 1.2-.7 2.2-1.7 2.6V5h-3.6v11.1c-1-.4-1.7-1.4-1.7-2.6V5H8Z" />
    </svg>
  )
}

function Arrow({ direction }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20">
      <path d={direction === 'left' ? 'm12.5 4-6 6 6 6' : 'm7.5 4 6 6-6 6'} />
    </svg>
  )
}

function Navigation({ date, today, onChange }) {
  const isToday = date === today
  return (
    <>
      <nav className="global-nav" aria-label="전역 탐색">
        <div className="nav-inner">
          <a className="brand" href="#top" aria-label="오늘의 한 끼 홈">
            <Mark />
            <span>오늘의 한 끼</span>
          </a>
          <span className="location">Busan Electric · Welstory</span>
        </div>
      </nav>
      <nav className="sub-nav" aria-label="날짜 선택">
        <div className="sub-nav-inner">
          <div>
            <span className="sub-label">점심 식단</span>
            <span className="sub-date">{formatDate(date)}</span>
          </div>
          <div className="date-actions">
            <button className="icon-button" onClick={() => onChange(moveDate(date, -1))} aria-label="이전 날짜">
              <Arrow direction="left" />
            </button>
            {!isToday && <button className="today-button" onClick={() => onChange(today)}>오늘</button>}
            <button className="icon-button" onClick={() => onChange(moveDate(date, 1))} disabled={isToday} aria-label="다음 날짜">
              <Arrow direction="right" />
            </button>
          </div>
        </div>
      </nav>
    </>
  )
}

function StatusBadge({ status }) {
  const labels = {
    READY: '식단 준비 완료',
    WAITING: '사진 확인 중',
    UNAVAILABLE: '운영하지 않음',
    ERROR: '연결 확인 필요',
  }
  return <span className={`status-badge status-${status.toLowerCase()}`}>{labels[status] || status}</span>
}

function Hero({ data, date }) {
  const eyebrow = data?.status === 'READY' ? 'TODAY’S LUNCH' : 'MEAL UPDATE'
  return (
    <header className="hero" id="top">
      <div className="hero-copy">
        <span className="eyebrow">{eyebrow}</span>
        <h1>오늘, 어떤<br />한 끼를 고를까요?</h1>
        <p>{formatDate(date)} · {data?.restaurantName || '삼성전기 부산사업장'}</p>
        {data && <StatusBadge status={data.status} />}
      </div>
      <div className="hero-index" aria-hidden="true">
        <span>{String(data?.meals?.length || 0).padStart(2, '0')}</span>
        <small>CHOICES</small>
      </div>
    </header>
  )
}

function MealCard({ meal, index }) {
  const dark = index % 3 === 1
  return (
    <article className={`meal-card ${dark ? 'meal-card-dark' : ''}`}>
      <div className="meal-image-wrap">
        {meal.imageUrl ? (
          <img
            src={meal.imageUrl}
            alt={`${meal.name} 식단 사진`}
            className="meal-image"
            loading={index < 2 ? 'eager' : 'lazy'}
            decoding="async"
          />
        ) : (
          <div className="image-placeholder" role="img" aria-label="식단 사진 준비 중">
            <Mark />
            <span>사진 준비 중</span>
          </div>
        )}
      </div>
      <div className="meal-copy">
        <span className="meal-number">{String(index + 1).padStart(2, '0')}</span>
        <p className="course-name">{meal.courseName || '오늘의 메뉴'}</p>
        <h2>{meal.name}</h2>
        {meal.description && <p className="description">{meal.description}</p>}
      </div>
    </article>
  )
}

function LoadingView() {
  return (
    <section className="loading-grid" aria-label="식단을 불러오는 중" aria-busy="true">
      {[0, 1, 2].map((item) => <div className="skeleton" key={item} />)}
    </section>
  )
}

function EmptyView({ data, error, onRetry }) {
  const title = error ? '잠시 연결이 매끄럽지 않아요.' : data?.status === 'UNAVAILABLE' ? '이 날짜의 식단이 없습니다.' : '따뜻한 한 끼를 준비하고 있어요.'
  const copy = error?.message || data?.message || '식단 사진은 늦어도 오전 10시 40분쯤 등록되는 편이에요.'
  return (
    <section className="empty-view">
      <span className="empty-symbol" aria-hidden="true">○</span>
      <h2>{title}</h2>
      <p>{copy}</p>
      {(error || data?.status === 'WAITING') && <button className="primary-button" onClick={onRetry}>다시 확인</button>}
    </section>
  )
}

function Footer({ data }) {
  const updated = formatTime(data?.lastUpdatedAt)
  return (
    <footer>
      <div className="footer-inner">
        <div>
          <Mark />
          <strong>오늘의 한 끼</strong>
        </div>
        <p>삼성 웰스토리에서 제공하는 식단 정보를 보기 편하게 전해드립니다.</p>
        <p className="fine-print">{updated ? `마지막 업데이트 ${updated}` : '식단 업데이트를 기다리는 중입니다.'} · 이미지와 메뉴 정보의 권리는 원 제공처에 있습니다.</p>
      </div>
    </footer>
  )
}

export default function App() {
  const today = useMemo(() => toLocalDateString(new Date()), [])
  const [date, setDate] = useState(today)
  const { status, data, error, reload } = useMeals(date)
  const hasMeals = data?.meals?.length > 0

  return (
    <div className="app-shell">
      <Navigation date={date} today={today} onChange={setDate} />
      <main>
        <Hero data={data} date={date} />
        {status === 'loading' && <LoadingView />}
        {hasMeals && (
          <section className="meal-grid" aria-label={`${formatDate(date)} 식단`}>
            {data.meals.map((meal, index) => <MealCard meal={meal} index={index} key={meal.id} />)}
          </section>
        )}
        {!hasMeals && status !== 'loading' && <EmptyView data={data} error={error} onRetry={reload} />}
      </main>
      <Footer data={data} />
    </div>
  )
}
