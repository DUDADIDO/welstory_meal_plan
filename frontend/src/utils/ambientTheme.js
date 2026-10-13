const weatherShade = { clear: 0, cloudy: 0.22, fog: 0.18, rain: 0.36, snow: 0.1, storm: 0.52 }
const mix = (from, to, amount) => from.map((channel, i) => Math.round(channel + (to[i] - channel) * amount))
const rgb = channels => `rgb(${channels.join(' ')})`
const luminance = channels => channels.map(channel => {
  const value = channel / 255
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}).reduce((sum, value, i) => sum + value * [0.2126, 0.7152, 0.0722][i], 0)
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + 0.05) / (Math.min(luminance(a), luminance(b)) + 0.05)

export function ambientTheme(condition, sky) {
  const base = weatherShade[condition] ?? 0
  const shade = Math.min(0.96, base + (0.96 - base) * sky.night)
  const warm = (sky.glow || 0) * (1 - shade) * 0.2
  const surface = mix(mix([255, 255, 255], [24, 35, 51], shade), [255, 207, 165], warm)
  const darkInk = [20, 29, 42], lightInk = [244, 247, 252]
  let lightText = contrast(surface, lightInk) > contrast(surface, darkInk)
  let ink = lightText ? lightInk : darkInk
  if (contrast(surface, ink) < 4.5) {
    lightText = contrast(surface, [255, 255, 255]) > contrast(surface, [0, 0, 0])
    ink = lightText ? [255, 255, 255] : [0, 0, 0]
  }
  let soft = mix(surface, ink, 0.045)
  if (contrast(soft, ink) < 4.5) soft = surface
  let muted = ink
  for (let amount = 0.72; amount <= 1; amount += 0.02) {
    const candidate = mix(surface, ink, amount)
    if (Math.min(contrast(surface, candidate), contrast(soft, candidate)) >= 4.5) { muted = candidate; break }
  }
  const alpha = lightText ? '255 255 255' : '0 0 0'
  return { shade, lightText, variables: {
    '--theme-surface': rgb(surface), '--theme-soft': rgb(soft),
    '--theme-ink': rgb(ink), '--theme-muted': rgb(muted),
    '--theme-line': rgb(mix(surface, ink, 0.2)), '--theme-border': `rgb(${alpha} / 0.12)`,
    '--theme-subtle': `rgb(${alpha} / 0.045)`, '--theme-hover': `rgb(${alpha} / 0.08)`,
    '--theme-rating-empty': rgb(mix(surface, ink, 0.3)),
    '--weather-rain': lightText ? 'rgb(172 206 240 / 0.6)' : 'rgb(68 105 143 / 0.65)',
    '--weather-cloud': lightText ? 'rgb(131 151 178 / 0.22)' : 'rgb(255 255 255 / 0.7)',
  } }
}
