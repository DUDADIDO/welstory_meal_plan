import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { advanceConfetti, createConfettiBurst } from '../../utils/confetti'

export default function SpecialMealCelebration() {
  const anchor = useRef(null)
  const canvas = useRef(null)

  useEffect(() => {
    const surface = canvas.current
    const context = surface.getContext('2d')
    if (!context) return
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    let particles = []
    let frame = 0
    let previousTime = 0
    let width = 0, height = 0
    let promote = false
    let blockedPointer = false

    function resize() {
      width = window.innerWidth
      height = window.innerHeight
      const scale = Math.min(window.devicePixelRatio || 1, 2)
      surface.width = Math.round(width * scale)
      surface.height = Math.round(height * scale)
      context.setTransform(scale, 0, 0, scale, 0, 0)
    }

    function draw(time) {
      frame = 0
      // The celebration layer never intercepts page input.
      if (promote && surface.showPopover) {
        if (surface.matches(':popover-open')) surface.hidePopover()
        surface.showPopover()
      }
      promote = false
      const elapsed = previousTime ? Math.min((time - previousTime) / 1000, 0.035) : 1 / 60
      previousTime = time
      particles = advanceConfetti(particles, elapsed)
      context.clearRect(0, 0, width, height)
      // Particles use page coordinates; only the canvas is fixed to the viewport.
      const scrollX = window.scrollX, scrollY = window.scrollY
      for (const particle of particles) {
        context.save()
        context.globalAlpha = Math.min(1, Math.max(0, (particle.life - particle.age) / 0.7))
        context.translate(particle.x - scrollX, particle.y - scrollY)
        context.rotate(particle.rotation)
        context.scale(1, Math.cos(particle.phase + particle.age * 8) * 0.8 + 0.2)
        context.fillStyle = particle.color
        if (particle.dot) {
          context.beginPath()
          context.arc(0, 0, particle.width / 2, 0, Math.PI * 2)
          context.fill()
        } else context.fillRect(-particle.width / 2, -particle.height / 2, particle.width, particle.height)
        context.restore()
      }
      if (particles.length) frame = requestAnimationFrame(draw)
      else {
        previousTime = 0
        if (surface.showPopover && surface.matches(':popover-open')) surface.hidePopover()
      }
    }

    function launch(x, y) {
      if (motion.matches) return
      // New bursts join the live particles instead of resetting the animation.
      particles.push(...createConfettiBurst(x, y))
      promote = true
      if (!frame) frame = requestAnimationFrame(draw)
    }

    function excluded(target) {
      if (!(target instanceof Element)) return true
      if (document.querySelector('dialog[open], [role="dialog"][aria-modal="true"]')) return true
      if (target.closest('dialog, [role="dialog"], [role="menu"], [role="listbox"]')) return true
      if (target.closest('[data-confetti-trigger]')) return false
      return Boolean(target.closest('button, a, input, textarea, select, label, summary, [contenteditable]:not([contenteditable="false"]), [tabindex]:not([tabindex="-1"]), [role="button"], [role="checkbox"], [role="radio"], [role="switch"], [role="slider"], [role="tab"], [role="menuitem"], [data-no-confetti]'))
    }

    function pointerDown(event) {
      // Remember backdrop/control gestures even if they remove a dialog before click fires.
      blockedPointer = excluded(event.target)
    }

    function click(event) {
      if ((event.detail > 0 && blockedPointer) || excluded(event.target)) return
      if (event.detail > 0) launch(event.clientX + window.scrollX, event.clientY + window.scrollY)
      else if (event.target instanceof Element) {
        const box = event.target.getBoundingClientRect()
        launch(box.left + box.width / 2 + window.scrollX, box.top + box.height / 2 + window.scrollY)
      }
    }

    function motionChanged() {
      if (!motion.matches) return
      cancelAnimationFrame(frame)
      frame = 0
      previousTime = 0
      particles = []
      context.clearRect(0, 0, width, height)
      if (surface.showPopover && surface.matches(':popover-open')) surface.hidePopover()
    }

    resize()
    const box = anchor.current.getBoundingClientRect()
    launch(box.left + window.scrollX, box.top + window.scrollY)
    document.addEventListener('click', click, true)
    document.addEventListener('pointerdown', pointerDown, true)
    window.addEventListener('resize', resize)
    motion.addEventListener('change', motionChanged)
    return () => {
      cancelAnimationFrame(frame)
      document.removeEventListener('click', click, true)
      document.removeEventListener('pointerdown', pointerDown, true)
      window.removeEventListener('resize', resize)
      motion.removeEventListener('change', motionChanged)
      if (surface.showPopover && surface.matches(':popover-open')) surface.hidePopover()
    }
  }, [])

  return <>
    <span ref={anchor} aria-hidden="true" className="pointer-events-none absolute left-1/2 top-1/2 size-px" />
    {createPortal(<canvas ref={canvas} popover="manual" aria-hidden="true" className="special-meal-confetti" />, document.body)}
  </>
}
