import { useCallback, useEffect, useRef, useState } from 'react'
import { adminApi } from '../services/http'

export function useServerLogs() {
  const [logs, setLogs] = useState([])
  const [paused, setPaused] = useState(false)
  const lastSequence = useRef(0)

  const load = useCallback(async () => {
    if (paused) return
    try {
      const incoming = await adminApi.getLogs(lastSequence.current, lastSequence.current ? 500 : 200)
      if (!incoming.length) return
      lastSequence.current = incoming.at(-1).sequence
      setLogs((previous) => [...previous, ...incoming].slice(-500))
    } catch {
      // 상태 API가 인증/연결 오류를 표시하므로 로그 폴링 오류는 중복 노출하지 않는다.
    }
  }, [paused])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, 5_000)
    return () => window.clearInterval(timer)
  }, [load])

  const clear = useCallback(() => setLogs([]), [])
  return { logs, paused, setPaused, clear }
}
