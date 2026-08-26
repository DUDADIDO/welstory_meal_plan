async function request(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  })

  if (!response.ok) {
    throw new Error(`요청을 처리하지 못했습니다. (${response.status})`)
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
}
