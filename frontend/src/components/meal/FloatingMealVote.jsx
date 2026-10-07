import { useCallback, useEffect, useRef, useState } from 'react'
import { useMealChoices } from '../../hooks/useMealChoices'
import KofiLink from '../common/KofiLink'

function VotePanel({ date, meals, onClose }) {
  const vote = useMealChoices(date)
  const dialog = useRef(null)
  useEffect(() => {
    const panel = dialog.current
    const overflow = document.body.style.overflow
    const previousFocus = document.activeElement
    document.body.style.overflow = 'hidden'
    panel.showModal()
    return () => {
      panel.close()
      document.body.style.overflow = overflow
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [])
  const total = meals.reduce((sum, meal) => sum + (vote.summary?.counts[meal.id] || 0), 0)
  const disabled = vote.loading || vote.saving || !vote.summary || vote.waitSeconds > 0 || vote.remainingToday === 0

  return <dialog ref={dialog} aria-modal="true" aria-labelledby="floating-vote-title" id="floating-meal-vote"
    onCancel={event => { event.preventDefault(); onClose() }}
    onKeyDown={event => {
      if (event.key !== 'Tab') return
      const buttons = [...event.currentTarget.querySelectorAll('button:not(:disabled)')]
      const first = buttons[0], last = buttons.at(-1)
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
    }}
    onMouseDown={event => {
      if (event.target !== event.currentTarget) return
      const box = event.currentTarget.getBoundingClientRect()
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose()
    }}
    className="fixed inset-0 m-auto flex h-[calc(100dvh-2rem)] max-h-none w-[min(28rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-3xl bg-surface p-0 text-ink shadow-2xl outline-none ring-1 ring-[var(--theme-border)] backdrop:bg-black/45 backdrop:backdrop-blur-sm">
    <header className="flex shrink-0 items-start justify-between gap-3 border-b border-line/50 px-5 py-4">
      <div><h2 id="floating-vote-title" className="text-base font-bold">오늘 뭐 먹을까요?</h2>
        <p className="mt-1 text-xs text-muted">{total}명 참여 · 한 사람당 한 메뉴</p></div>
      <button type="button" onClick={onClose} aria-label="투표창 닫기" className="grid size-7 shrink-0 place-items-center rounded-full bg-soft text-lg text-muted">×</button>
    </header>
    <div className="flex min-h-0 flex-1 flex-col px-4 py-3">
      <p className="mb-3 shrink-0 text-[0.65rem] text-muted">다른 메뉴를 누르면 변경 · 같은 메뉴를 누르면 취소</p>
      <div className="flex min-h-0 flex-1 flex-col gap-2 [@media(max-height:640px)]:grid [@media(max-height:640px)]:auto-rows-fr [@media(max-height:640px)]:grid-cols-2">
        {meals.map(meal => {
          const count = vote.summary?.counts[meal.id] || 0
          const percent = total ? Math.round(count / total * 100) : 0
          const selected = vote.summary?.myMealId === meal.id
          return <button key={meal.id} type="button" onClick={() => vote.choose(meal.id)} disabled={disabled}
            aria-pressed={selected} aria-label={`${meal.name} 투표${selected ? ', 내 선택' : ''}`} title={meal.name}
            className={`flex min-h-0 w-full flex-1 flex-col justify-center rounded-2xl px-3 py-2 text-left ring-1 transition disabled:opacity-60 [@media(max-height:640px)]:py-1.5 ${selected ? 'bg-apple-blue/10 ring-apple-blue/50' : 'bg-soft/50 ring-[var(--theme-border)] hover:bg-soft'}`}>
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0"><p className="text-[0.6rem] text-muted">{meal.courseName}</p><p className="mt-0.5 truncate text-xs font-semibold">{meal.name}</p></div>
              <span className="shrink-0 text-[0.65rem] font-semibold text-apple-blue">{selected ? '✓ ' : ''}{count}명</span>
            </div>
            <div className="mt-2 flex items-center gap-2 [@media(max-height:640px)]:mt-1"><div className="h-1 flex-1 overflow-hidden rounded-full bg-line/40"><div className="h-full rounded-full bg-apple-blue/60 transition-[width]" style={{ width: `${percent}%` }} /></div><span className="w-8 text-right text-[0.6rem] tabular-nums text-muted">{percent}%</span></div>
          </button>
        })}
      </div>
    </div>
    <footer className="shrink-0 border-t border-line/50 px-5 py-3">
      <p role="status" className="text-xs font-medium text-muted">{vote.loading ? '투표 확인 중…' : vote.saving ? '저장 중…' : vote.remainingToday === 0 ? '오늘 10회를 모두 사용했어요' : vote.waitSeconds > 0 ? `${vote.waitSeconds}초 후 다시 선택할 수 있어요` : `오늘 ${vote.remainingToday}회 남았어요`}</p>
      <p className="mt-1 text-[0.6rem] text-muted">5초에 1회 · 하루 10회 · 변경·취소도 1회 사용</p>
      {vote.error && <p role="alert" className="mt-2 text-xs text-red-500">{vote.error}</p>}
    </footer>
  </dialog>
}

export default function FloatingMealVote({ date, meals, canVote }) {
  const [open, setOpen] = useState(false)
  const trigger = useRef(null)
  const close = useCallback(() => { setOpen(false); trigger.current?.focus() }, [])

  return <>
    {open && canVote && <VotePanel date={date} meals={meals} onClose={close} />}
    <aside className="fixed right-5 z-40 flex items-center gap-3" style={{ bottom: 'calc(1.25rem + env(safe-area-inset-bottom, 0px))' }}>
    <KofiLink floating />
    {canVote && <button ref={trigger} type="button" onClick={() => setOpen(value => !value)} aria-label="오늘 메뉴 투표" title="오늘 메뉴 투표"
      aria-expanded={open} aria-controls={open ? 'floating-meal-vote' : undefined}
      className="grid size-13 place-items-center rounded-full bg-apple-blue text-white shadow-lg ring-1 ring-white/15 transition hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-apple-blue">
      <svg aria-hidden="true" viewBox="0 0 24 24" className="size-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M8 3h8v9H8z" /><path d="m10 7 1.5 1.5L14 6M6 10H4l-2 6v5h20v-5l-2-6h-2M2 16h20M10 12h4" /></svg>
    </button>}
  </aside>
  </>
}
