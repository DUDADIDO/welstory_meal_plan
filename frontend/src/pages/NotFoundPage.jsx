import { Link, useRouteError } from 'react-router-dom'

export default function NotFoundPage() {
  const error = useRouteError()
  return (
    <main className="grid min-h-dvh place-items-center bg-canvas px-6 text-center text-ink">
      <div>
        <p className="text-xs font-bold tracking-[0.2em] text-apple-blue">{error?.status || '404'}</p>
        <h1 className="mt-3 text-4xl font-bold tracking-tight">페이지를 찾을 수 없습니다.</h1>
        <p className="mt-3 text-sm text-muted">주소를 확인하거나 식단 화면으로 돌아가 주세요.</p>
        <Link to="/" className="mt-7 inline-flex rounded-full bg-apple-blue px-5 py-2.5 text-sm font-semibold text-white">식단으로 돌아가기</Link>
      </div>
    </main>
  )
}
