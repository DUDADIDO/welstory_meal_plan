import test from 'node:test'
import assert from 'node:assert/strict'
import { getMoonPosition, getPosition } from 'suncalc'
import { moonTilt, nearestRotation } from './moonTilt.js'

const dot = (a, b) => a.reduce((sum, value, i) => sum + value * b[i], 0)
const normalized = vector => vector.map(value => value / Math.hypot(...vector))
const direction = ({ azimuth, altitude }) => {
  const a = azimuth * Math.PI / 180, h = altitude * Math.PI / 180
  return [Math.cos(h) * Math.sin(a), Math.cos(h) * Math.cos(a), Math.sin(h)]
}
const difference = (a, b) => Math.abs(((a - b + 180) % 360 + 360) % 360 - 180)

test('rendered bright side points toward projected sunlight, with correct SVG sign and degree units', () => {
  for (const time of ['18:00', '20:00', '22:00']) {
    const date = new Date(`2026-10-13T${time}:00+09:00`)
    const moon = direction(getMoonPosition(date, 35.09, 128.87))
    const sun = direction(getPosition(date, 35.09, 128.87))
    const up = normalized([-moon[0] * moon[2], -moon[1] * moon[2], 1 - moon[2] * moon[2]])
    const right = normalized([moon[1], -moon[0], 0])
    const expected = Math.atan2(dot(sun, right), dot(sun, up)) * 180 / Math.PI
    // Geocentric illumination and observer-projected positions differ slightly.
    assert(difference(moonTilt(+date, 0.1) + 90, expected) < 3)
    assert(difference(moonTilt(+date, 0.9) - 90, expected) < 3)
  }
})

test('orientation varies with time/location and avoids full spins across the angle boundary', () => {
  const start = Date.parse('2026-10-13T18:00:00+09:00')
  assert(difference(moonTilt(start, 0.1), moonTilt(start + 4 * 3600_000, 0.1)) > 5)
  assert(difference(moonTilt(start, 0.1), moonTilt(start, 0.1, -35.09, 128.87)) > 5)
  assert.equal(nearestRotation(179, -179), 181)
  assert.equal(nearestRotation(-179, 179), -181)
  assert.equal(moonTilt(NaN, 0.1), 0)
  assert.equal(moonTilt(1e20, 0.1), 0)
  assert.equal(moonTilt(start, 0.1, 91, 128.87), 0)
})
