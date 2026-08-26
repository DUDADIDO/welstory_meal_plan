import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../services/http'

export function useAdminStatus() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)

  const load = useCallback(async () => {
    try {
      setData(await adminApi.getStatus())
      setError(null)
    } catch (loadError) {
      setError(loadError)
    }
  }, [])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, 30_000)
    return () => window.clearInterval(timer)
  }, [load])

  const refreshToday = useCallback(async () => {
    setRefreshing(true)
    try {
      setData(await adminApi.refreshToday())
      setError(null)
    } catch (refreshError) {
      setError(refreshError)
    } finally {
      setRefreshing(false)
    }
  }, [])

  return { data, error, refreshing, refreshToday }
}
