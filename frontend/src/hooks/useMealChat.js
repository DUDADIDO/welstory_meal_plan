import { useCallback, useEffect, useRef, useState } from 'react'
import { chatApi } from '../services/http'

// Share initialization during React StrictMode remounts so only one identity is issued.
let identityRequest
function identify() {
  if (!identityRequest) {
    identityRequest = chatApi.identity().finally(() => { identityRequest = null })
  }
  return identityRequest
}

function mergeMessages(previous, incoming) {
  const messages = new Map(previous.map((message) => [message.id, message]))
  incoming.forEach((message) => messages.set(message.id, message))
  return [...messages.values()].sort((a, b) => a.id - b.id)
}

export function useMealChat(date, mealId) {
  const [messages, setMessages] = useState([])
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [sending, setSending] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [hasOlder, setHasOlder] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [sendError, setSendError] = useState('')
  const [now, setNow] = useState(Date.now())
  const controls = useRef(null)
  const sendingRef = useRef(false)
  const olderRef = useRef(false)
  const historyLoaded = useRef(false)
  const offset = useRef(0)
  const latestUser = useRef(null)

  const updateUser = useCallback((incoming) => {
    if (latestUser.current && Date.parse(latestUser.current.serverTime) > Date.parse(incoming.serverTime)) return
    latestUser.current = incoming
    offset.current = Date.parse(incoming.serverTime) - Date.now()
    setNow(Date.now() + offset.current)
    setUser(incoming)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let timer
    let identified = false
    let running = false
    const tick = window.setInterval(() => setNow(Date.now() + offset.current), 250)

    async function refresh() {
      if (running || controller.signal.aborted) return
      running = true
      window.clearTimeout(timer)
      try {
        if (!identified) {
          await identify()
          identified = true
        }
        if (controller.signal.aborted) return
        const room = await chatApi.messages(date, mealId, controller.signal)
        if (controller.signal.aborted) return
        setMessages((previous) => mergeMessages(previous, room.messages))
        updateUser(room.user)
        if (!historyLoaded.current) setHasOlder(room.hasOlder)
        setLoadError('')
      } catch (error) {
        if (error.name !== 'AbortError' && !controller.signal.aborted) {
          setLoadError(error.message)
          if (error.status === 401) identified = false
        }
      } finally {
        running = false
        if (!controller.signal.aborted) {
          setLoading(false)
          timer = window.setTimeout(() => {
            if (document.hidden) scheduleHidden()
            else refresh()
          }, 3000)
        }
      }
    }

    function scheduleHidden() {
      if (!controller.signal.aborted) timer = window.setTimeout(() => {
        if (document.hidden) scheduleHidden()
        else refresh()
      }, 3000)
    }

    function onVisibility() {
      if (!document.hidden) refresh()
    }

    controls.current = { controller, refresh, identifyAgain: () => { identified = false; refresh() } }
    refresh()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      controller.abort()
      window.clearTimeout(timer)
      window.clearInterval(tick)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [date, mealId, updateUser])

  const send = async (content) => {
    const signal = controls.current?.controller.signal
    if (sendingRef.current || !signal || signal.aborted) return false
    sendingRef.current = true
    setSending(true)
    setSendError('')
    try {
      const result = await chatApi.send({ date, mealId, content }, signal)
      if (signal.aborted) return false
      setMessages((previous) => mergeMessages(previous, [result.message]))
      updateUser(result.user)
      return true
    } catch (error) {
      if (error.name !== 'AbortError' && !signal.aborted) {
        setSendError(error.message)
        if (error.data?.user) updateUser(error.data.user)
        if (error.status === 401) controls.current?.identifyAgain()
      }
      return false
    } finally {
      sendingRef.current = false
      if (!signal.aborted) setSending(false)
    }
  }

  const loadOlder = async () => {
    if (olderRef.current || !messages.length || !hasOlder) return
    const signal = controls.current?.controller.signal
    olderRef.current = true
    setLoadingOlder(true)
    try {
      const room = await chatApi.messages(date, mealId, signal, messages[0].id)
      if (signal.aborted) return
      historyLoaded.current = true
      setMessages((previous) => mergeMessages(previous, room.messages))
      setHasOlder(room.hasOlder)
      updateUser(room.user)
      setLoadError('')
    } catch (error) {
      if (error.name !== 'AbortError' && !signal.aborted) setLoadError(error.message)
    } finally {
      olderRef.current = false
      if (!signal.aborted) setLoadingOlder(false)
    }
  }

  const remainingToday = user && now >= Date.parse(user.resetsAt) ? 50 : user?.remainingToday ?? 0
  const waitSeconds = user ? Math.max(0, Math.ceil((Date.parse(user.nextMessageAt) - now) / 1000)) : 0
  return {
    messages, user, loading, sending, loadingOlder, hasOlder,
    error: sendError || loadError, send, loadOlder, remainingToday, waitSeconds,
    retry: () => { setSendError(''); controls.current?.refresh() },
  }
}
