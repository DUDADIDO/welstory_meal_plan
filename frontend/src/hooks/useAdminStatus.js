import { useCallback, useEffect, useState } from 'react'
import { adminApi } from '../services/http'

export function useAdminStatus() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const [jobBusy, setJobBusy] = useState(false)

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
    const timer = window.setInterval(load, 3_000)
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

  const startCacheJob = useCallback(async (startDate, endDate, forceExisting = false) => {
    setJobBusy(true)
    try {
      const cacheJob = await adminApi.startCacheJob(startDate, endDate, forceExisting)
      setData((previous) => previous ? { ...previous, cacheJob } : previous)
      setError(null)
    } catch (jobError) {
      setError(jobError)
      throw jobError
    } finally {
      setJobBusy(false)
    }
  }, [])

  const cancelCacheJob = useCallback(async () => {
    setJobBusy(true)
    try {
      const cacheJob = await adminApi.cancelCacheJob()
      setData((previous) => previous ? { ...previous, cacheJob } : previous)
      setError(null)
    } catch (jobError) {
      setError(jobError)
    } finally {
      setJobBusy(false)
    }
  }, [])

  return { data, error, refreshing, jobBusy, refreshToday, startCacheJob, cancelCacheJob }
}
