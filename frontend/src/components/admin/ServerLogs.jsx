import { useEffect, useRef, useState } from 'react'
import { useServerLogs } from '../../hooks/useServerLogs'

const levelColor = { ERROR: 'text-red-400', WARN: 'text-amber-300', INFO: 'text-sky-300', DEBUG: 'text-white/45' }

export default function ServerLogs() {
  const { logs, paused, setPaused, clear } = useServerLogs()
  const [level, setLevel] = useState('ALL')
  const viewportRef = useRef(null)
  const visibleLogs = level === 'ALL' ? logs : logs.filter((entry) => entry.level === level)

  useEffect(() => {
    if (!paused && viewportRef.current) viewportRef.current.scrollTop = viewportRef.current.scrollHeight
  }, [visibleLogs, paused])

  return (
    <section className="mt-8 overflow-hidden rounded-[1.5rem] bg-[#151516] text-white shadow-sm ring-1 ring-black/10">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 px-5 py-4">
        <div><h2 className="text-base font-bold">서버 로그</h2><p className="mt-1 text-[0.68rem] text-white/40">최근 500건 · 5초마다 갱신 · 민감정보 마스킹</p></div>
        <div className="flex items-center gap-2">
          <select value={level} onChange={(event) => setLevel(event.target.value)} className="rounded-lg border border-white/10 bg-white/8 px-2 py-1.5 text-xs outline-none"><option value="ALL">전체</option><option value="INFO">INFO</option><option value="WARN">WARN</option><option value="ERROR">ERROR</option></select>
          <button type="button" onClick={() => setPaused((value) => !value)} className="rounded-lg bg-white/8 px-3 py-1.5 text-xs font-semibold hover:bg-white/12">{paused ? '재개' : '일시정지'}</button>
          <button type="button" onClick={clear} className="rounded-lg bg-white/8 px-3 py-1.5 text-xs font-semibold hover:bg-white/12">화면 지우기</button>
        </div>
      </div>
      <div ref={viewportRef} className="h-[24rem] overflow-auto p-4 font-mono text-[0.72rem] leading-5">
        {visibleLogs.length === 0 && <p className="text-white/30">표시할 로그가 없습니다.</p>}
        {visibleLogs.map((entry) => (
          <div key={entry.sequence} className="grid grid-cols-[5rem_3rem_minmax(0,1fr)] gap-2 border-b border-white/[0.035] py-1">
            <time className="text-white/35">{new Date(entry.timestamp).toLocaleTimeString('ko-KR', { hour12: false })}</time>
            <span className={levelColor[entry.level] || 'text-white/45'}>{entry.level}</span>
            <p className="min-w-0 break-words text-white/75"><span className="mr-2 text-white/30">{entry.logger.split('.').at(-1)}</span>{entry.message}{entry.exception && <span className="block text-red-300">{entry.exception}</span>}</p>
          </div>
        ))}
      </div>
    </section>
  )
}
