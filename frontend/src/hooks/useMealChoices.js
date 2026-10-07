import { useEffect, useRef, useState } from 'react'
import { mealChoiceApi } from '../services/http'
import { ensureAnonymousUser } from '../services/anonymous'

export function useMealChoices(date) {
  const [summary, setSummary] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [now, setNow] = useState(Date.now())
  const controller = useRef(null)
  const busy = useRef(false)
  const revision = useRef(0)
  const offset = useRef(0)

  function update(result) {
    offset.current = Date.parse(result.serverTime) - Date.now()
    setNow(Date.now() + offset.current)
    setSummary(result)
  }

  useEffect(() => {
    const control = new AbortController()
    controller.current = control
    let identified = false
    let fetching = false
    async function load() {
      if (control.signal.aborted || fetching || busy.current) return
      fetching = true
      const version = revision.current
      try {
        if (!identified) { await ensureAnonymousUser(); identified = true }
        if (control.signal.aborted) return
        const result = await mealChoiceApi.get(date, control.signal)
        if (!control.signal.aborted && version === revision.current) { update(result); setError('') }
      } catch (error) {
        if (!control.signal.aborted && version === revision.current) setError(error.message)
      } finally {
        fetching = false
        if (!control.signal.aborted) setLoading(false)
      }
    }
    load()
    const timer = window.setInterval(() => { if (!document.hidden) load() }, 30_000)
    const tick = window.setInterval(() => setNow(Date.now() + offset.current), 250)
    const visible = () => { if (!document.hidden) load() }
    document.addEventListener('visibilitychange', visible)
    return () => {
      control.abort(); window.clearInterval(timer); window.clearInterval(tick)
      document.removeEventListener('visibilitychange', visible)
    }
  }, [date])

  async function choose(mealId) {
    const signal = controller.current?.signal
    if (!signal || signal.aborted || busy.current || !summary) return
    busy.current = true
    revision.current++
    setSaving(true)
    setError('')
    try {
      await ensureAnonymousUser()
      if (signal.aborted) return
      const result = await mealChoiceApi.choose(date, summary.myMealId === mealId ? null : mealId, signal)
      if (!signal.aborted) update(result)
    } catch (error) {
      if (!signal.aborted) {
        setError(error.message)
        if (error.data?.summary) update(error.data.summary)
      }
    } finally {
      busy.current = false
      if (!signal.aborted) setSaving(false)
    }
  }

  return {
    summary, error, loading, saving, choose,
    waitSeconds: summary ? Math.max(0, Math.ceil((Date.parse(summary.nextVoteAt) - now) / 1000)) : 0,
    remainingToday: summary && now >= Date.parse(summary.resetsAt) ? 10 : summary?.remainingToday ?? 0,
  }
}
