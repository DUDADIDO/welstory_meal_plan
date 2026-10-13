import { getMoonIllumination, getMoonPosition } from 'suncalc'

export function moonTilt(timestamp, lunarPhase, latitude = 35.09, longitude = 128.87) {
  if (!Number.isFinite(timestamp) || !Number.isFinite(lunarPhase)
      || !Number.isFinite(latitude) || !Number.isFinite(longitude)
      || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return 0
  const date = new Date(timestamp)
  if (!Number.isFinite(date.getTime())) return 0
  const illumination = getMoonIllumination(date)
  const position = getMoonPosition(date, latitude, longitude)
  // SunCalc v2 returns degrees anticlockwise from zenith. SVG is clockwise,
  // and our unrotated crescent points right (waxing) or left (waning).
  const brightLimb = illumination.angle - position.parallacticAngle
  const baseDirection = lunarPhase < 0.5 ? 90 : -90
  const rotation = -brightLimb - baseDirection
  return ((rotation + 180) % 360 + 360) % 360 - 180
}

export function nearestRotation(previous, next) {
  if (!Number.isFinite(previous)) return next
  return previous + ((next - previous + 180) % 360 + 360) % 360 - 180
}
