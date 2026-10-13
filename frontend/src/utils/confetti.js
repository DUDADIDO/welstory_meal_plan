const colors = ['#0071e3', '#ff375f', '#ff9f0a', '#30d158', '#bf5af2', '#ffd60a']

export function createConfettiBurst(x, y, random = Math.random) {
  return Array.from({ length: 42 }, () => {
    const angle = (random() - 0.5) * 1.4
    const speed = 320 + random() * 280
    return {
      x, y, vx: Math.sin(angle) * speed, vy: -Math.cos(angle) * speed,
      age: 0, life: 4 + random() * 1.5, phase: random() * Math.PI * 2,
      rotation: random() * Math.PI, spin: (random() - 0.5) * 12,
      width: 5 + random() * 4, height: 7 + random() * 6,
      color: colors[Math.floor(random() * colors.length)], dot: random() < 0.18,
    }
  })
}

export function advanceConfetti(particles, seconds) {
  const drag = Math.exp(-0.9 * seconds)
  return particles.filter(particle => {
    particle.age += seconds
    particle.vx *= drag
    particle.vy += 780 * seconds
    particle.x += (particle.vx + Math.sin(particle.age * 5 + particle.phase) * 18) * seconds
    particle.y += particle.vy * seconds
    particle.rotation += particle.spin * seconds
    // Keep off-screen particles alive so scrolling does not erase a burst.
    return particle.age < particle.life
  })
}
