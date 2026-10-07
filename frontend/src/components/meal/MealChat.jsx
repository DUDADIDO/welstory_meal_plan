import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useMealChat } from '../../hooks/useMealChat'
import { formatDate } from '../../utils/date'

const messageTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
})

export default function MealChat({ date, meal, onClose }) {
  const chat = useMealChat(date, meal.id)
  const [draft, setDraft] = useState('')
  const panel = useRef(null)
  const textarea = useRef(null)
  const scrollArea = useRef(null)
  const follow = useRef(true)
  const olderScroll = useRef(null)
  const newest = chat.messages.at(-1)?.id

  useEffect(() => {
    const previouslyFocused = document.activeElement
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.current?.focus()
    function keydown(event) {
      if (event.key === 'Escape') onClose()
      if (event.key !== 'Tab') return
      const focusable = [...panel.current.querySelectorAll('button:not(:disabled), textarea:not(:disabled), [tabindex="0"]')]
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.current)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === panel.current)) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', keydown)
    return () => {
      document.body.style.overflow = overflow
      document.removeEventListener('keydown', keydown)
      previouslyFocused?.focus()
    }
  }, [onClose])

  useLayoutEffect(() => {
    const area = scrollArea.current
    if (!area) return
    if (olderScroll.current) {
      if (!chat.loadingOlder) {
        area.scrollTop = area.scrollHeight - olderScroll.current.height + olderScroll.current.top
        olderScroll.current = null
      }
    } else if (follow.current) {
      area.scrollTop = area.scrollHeight
    }
  }, [chat.messages, chat.loadingOlder])

  async function submit(event) {
    event.preventDefault()
    if (!draft.trim() || chat.sending || !chat.user || chat.waitSeconds || !chat.remainingToday) return
    const sentDraft = draft
    if (await chat.send(sentDraft)) {
      setDraft((current) => current === sentDraft ? '' : current)
      follow.current = true
      if (scrollArea.current) scrollArea.current.scrollTop = scrollArea.current.scrollHeight
      textarea.current?.focus()
    }
  }

  async function older() {
    const area = scrollArea.current
    olderScroll.current = { height: area.scrollHeight, top: area.scrollTop }
    await chat.loadOlder()
  }

  const disabled = !chat.user || chat.loading || chat.sending || chat.waitSeconds > 0 || chat.remainingToday === 0 || !draft.trim()

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-3 backdrop-blur-sm sm:p-6"
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section ref={panel} role="dialog" aria-modal="true" aria-labelledby="meal-chat-title" tabIndex={-1}
        className="flex h-[min(44rem,90dvh)] w-full max-w-xl flex-col overflow-hidden rounded-3xl bg-surface shadow-2xl outline-none">
        <header className="flex shrink-0 items-start justify-between gap-4 border-b border-line/50 px-5 py-4">
          <div className="min-w-0">
            <p className="text-xs text-muted">{formatDate(date)} · {meal.courseName || '오늘의 메뉴'}</p>
            <h2 id="meal-chat-title" className="mt-1 truncate text-lg font-bold">{meal.name} 채팅</h2>
          </div>
          <button type="button" onClick={onClose} aria-label="채팅방 닫기"
            className="grid size-9 shrink-0 place-items-center rounded-full bg-soft text-2xl text-muted hover:text-ink">×</button>
        </header>

        <div className="flex shrink-0 flex-wrap items-center justify-between gap-2 bg-soft/60 px-5 py-3 text-xs">
          <p><span className="text-muted">내 익명 이름 </span><strong>{chat.user?.nickname || '발급 중…'}</strong></p>
          <span className="text-muted">오늘 남은 채팅 <strong className="text-ink">{chat.user ? chat.remainingToday : '—'}</strong> / 50</span>
        </div>

        <div ref={scrollArea} tabIndex={0} aria-label="채팅 메시지" className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4 outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-apple-blue"
          onScroll={() => {
            const area = scrollArea.current
            follow.current = area.scrollHeight - area.scrollTop - area.clientHeight < 80
          }}>
          {chat.hasOlder && <div className="mb-4 text-center"><button type="button" onClick={older} disabled={chat.loadingOlder}
            className="rounded-full bg-soft px-4 py-2 text-xs font-semibold text-muted disabled:opacity-50">{chat.loadingOlder ? '불러오는 중…' : '이전 대화 보기'}</button></div>}
          {chat.loading && <p role="status" className="py-12 text-center text-sm text-muted">채팅방을 여는 중…</p>}
          {!chat.loading && !chat.messages.length && <div className="py-12 text-center">
            <p className="text-sm font-semibold">이 식단에 대해 이야기해 보세요</p>
            <p className="mt-2 text-xs text-muted">맛, 추천 조합, 궁금한 점을 자유롭게 나눠요.</p>
          </div>}
          <ol className="space-y-4">
            {chat.messages.map((message) => <li key={message.id} className={`flex flex-col ${message.mine ? 'items-end' : 'items-start'}`}>
              <p className="mb-1.5 text-xs font-semibold text-muted">{message.nickname}{message.mine && <span className="ml-1.5 text-apple-blue">나</span>}</p>
              <p className={`max-w-[88%] whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2.5 text-sm leading-6 [overflow-wrap:anywhere] ${message.mine ? 'rounded-tr-sm bg-apple-blue text-white' : 'rounded-tl-sm bg-soft'}`}>{message.content}</p>
              <time dateTime={message.createdAt} className="mt-1 text-[0.65rem] text-muted">{messageTime.format(new Date(message.createdAt))}</time>
            </li>)}
          </ol>
        </div>
        <span className="sr-only" role="status" aria-live="polite">{newest ? `채팅 메시지 ${chat.messages.length}개` : ''}</span>

        <form onSubmit={submit} className="shrink-0 border-t border-line/50 p-4 sm:px-5">
          {chat.error && <div role="alert" className="mb-3 flex items-center justify-between gap-2 text-xs text-red-500">
            <p>{chat.error}</p>
            <button type="button" onClick={chat.retry} className="shrink-0 underline">새로고침</button>
          </div>}
          <label htmlFor="meal-chat-draft" className="sr-only">채팅 메시지 작성</label>
          <div className="flex items-end gap-2">
            <textarea ref={textarea} id="meal-chat-draft" rows={2} maxLength={500} value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) submit(event)
              }}
              placeholder={chat.user && !chat.remainingToday ? '오늘의 채팅을 모두 사용했어요' : '메시지 입력 · Enter로 전송'}
              className="min-w-0 flex-1 resize-none rounded-2xl bg-soft px-4 py-3 text-sm outline-none focus:ring-2 focus:ring-apple-blue" />
            <button type="submit" disabled={disabled} className="h-12 min-w-16 rounded-2xl bg-apple-blue px-3 text-sm font-semibold text-white disabled:opacity-40">
              {chat.sending ? '전송 중' : chat.waitSeconds > 0 ? `${chat.waitSeconds}초` : '전송'}
            </button>
          </div>
          <div className="mt-2 flex justify-between gap-2 text-[0.65rem] text-muted">
            <p>5초에 1개 · 모든 채팅방 합산 하루 50개 · 자정 초기화</p>
            <span>{draft.length}/500</span>
          </div>
        </form>
      </section>
    </div>
  )
}
