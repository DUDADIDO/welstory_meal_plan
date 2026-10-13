import test from 'node:test'
import assert from 'node:assert/strict'
import { ambientTheme } from './ambientTheme.js'

const channels = color => color.match(/[\d.]+/g).slice(0, 3).map(Number)
const luminance = color => channels(color).map(channel => {
  const value = channel / 255
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}).reduce((sum, value, i) => sum + value * [0.2126, 0.7152, 0.0722][i], 0)
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + 0.05) / (Math.min(luminance(a), luminance(b)) + 0.05)

test('card and control text remain readable throughout weather, twilight and night transitions', () => {
  for (const condition of ['clear', 'cloudy', 'fog', 'rain', 'snow', 'storm']) {
    for (let step = 0; step <= 100; step++) for (const glow of [0, 0.5, 1]) {
      const { variables } = ambientTheme(condition, { night: step / 100, glow })
      for (const surface of ['--theme-surface', '--theme-soft']) for (const text of ['--theme-ink', '--theme-muted']) {
        assert(contrast(variables[surface], variables[text]) >= 4.5, `${condition} night=${step}% glow=${glow}: ${surface}/${text}`)
      }
    }
  }
})

test('clouds, rain and storms darken surfaces; advancing night darkens each weather continuously', () => {
  const surface = (condition, night = 0) => luminance(ambientTheme(condition, { night, glow: 0 }).variables['--theme-surface'])
  assert(surface('clear') > surface('cloudy'))
  assert(surface('cloudy') > surface('rain'))
  assert(surface('rain') > surface('storm'))
  for (const condition of ['clear', 'cloudy', 'fog', 'rain', 'snow', 'storm']) {
    let previous = surface(condition)
    for (let step = 1; step <= 100; step++) {
      const next = surface(condition, step / 100)
      assert(next <= previous)
      previous = next
    }
  }
})
