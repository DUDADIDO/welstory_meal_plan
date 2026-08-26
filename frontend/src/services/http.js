async function request(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  })

  if (!response.ok) {
    let detail
    try {
      const errorBody = await response.json()
      detail = errorBody.detail || errorBody.message
    } catch {
      detail = null
    }
    throw new Error(detail || `요청을 처리하지 못했습니다. (${response.status})`)
  }
  if (response.status === 204) {
    return null
  }
  
  return response.json()
}

export const mealApi = {
  getByDate: (date, signal) => request(`/api/meals?date=${date}`, { signal }),
}

export const ratingApi = {
  getByDate: (date, clientId, signal) => request(
    `/api/ratings?date=${date}&clientId=${encodeURIComponent(clientId)}`,
    { signal },
  ),
  save: (payload) => request('/api/ratings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  }),
}

export const adminApi = {
  getStatus: () => request('/api/admin/status', { cache: 'no-store', credentials: 'same-origin' }),
  refreshToday: () => request('/api/admin/refresh', {
    method: 'POST',
    credentials: 'same-origin',
  }),
  startCacheJob: (startDate, endDate) => request('/api/admin/cache-jobs', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ startDate, endDate }),
  }),
  cancelCacheJob: () => request('/api/admin/cache-jobs/current', {
    method: 'DELETE',
    credentials: 'same-origin',
  }),
  getLogs: (after = 0, limit = 200) => request(
    `/api/admin/logs?after=${after}&limit=${limit}`,
    { cache: 'no-store', credentials: 'same-origin' },
  ),
}
export const visitorApi = {
  record: (clientId) => request('/api/visitors', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ clientId }),
  }),
}