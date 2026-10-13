import test from 'node:test'
import assert from 'node:assert/strict'
import { dateForLunarDay, lunarForDate, moonLitPath, seoulDate } from './lunarMoon.js'

test('Korean lunar conversion handles a known leap month and Seoul midnight', () => {
  const leap = lunarForDate('2017-06-24')
  assert.equal(leap.month, 5)
  assert.equal(leap.day, 1)
  assert.equal(leap.intercalation, true)
  assert.equal(seoulDate(Date.parse('2026-10-12T15:00:00Z')), '2026-10-13')
  assert.equal(lunarForDate('2026-02-31'), null)
  assert.equal(lunarForDate('2051-01-01'), null)
})

test('new/full/waning moons and lunar-day presets preserve lunar month including leap month', () => {
  for (const date of ['2017-06-24', '2026-10-13']) {
    const first = lunarForDate(dateForLunarDay(date, 1))
    const full = lunarForDate(dateForLunarDay(date, 15))
    const waxing = lunarForDate(dateForLunarDay(date, 8))
    const waning = lunarForDate(dateForLunarDay(date, 27))
    assert.equal(first.phase, 0)
    assert.equal(first.illuminated, 0)
    assert.equal(full.phase, 0.5)
    assert.equal(full.illuminated, 1)
    assert.equal(waxing.phase, 0.25)
    assert(waning.phase > 0.75)
    assert.equal(full.intercalation, first.intercalation)
    assert.equal(moonLitPath(first.phase), '')
    assert(moonLitPath(waxing.phase).includes('A48 48 0 0 1'))
    assert(moonLitPath(waning.phase).includes('A48 48 0 0 0'))
  }
})
