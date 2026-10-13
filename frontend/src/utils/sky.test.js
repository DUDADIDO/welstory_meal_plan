import test from 'node:test'
import assert from 'node:assert/strict'
import { solarTimesFor, skyState } from './sky.js'

test('KMA daylight calculation uses the Korean calendar day even just after midnight', () => {
  const time = Date.parse('2026-10-12T15:05:00Z')
  const solar = solarTimesFor(time)
  assert(Date.parse(solar.sunrise) > Date.parse('2026-10-12T21:00:00Z'))
  assert(Date.parse(solar.sunrise) < Date.parse('2026-10-12T22:00:00Z'))
  assert(Date.parse(solar.sunset) > Date.parse('2026-10-13T08:00:00Z'))
  assert(Date.parse(solar.sunset) < Date.parse('2026-10-13T09:00:00Z'))
  assert.equal(skyState(solar, time).night, 1)
  assert.equal(skyState(solar, Date.parse('2026-10-13T03:00:00Z')).night, 0)
})
