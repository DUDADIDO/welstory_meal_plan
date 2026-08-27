const particles = [
  { x: '-7.5rem', y: '-3rem', fall: '2rem', gravity: '7.5rem', drift: '1.1rem', endDrift: '1.7rem', delay: '0ms', turn: '-560deg', color: '#0071e3', shape: 'ribbon' },
  { x: '-6rem', y: '-1.5rem', fall: '2.5rem', gravity: '8.5rem', drift: '-0.8rem', endDrift: '-1.3rem', delay: '70ms', turn: '480deg', color: '#ff375f', shape: 'dot' },
  { x: '-4.5rem', y: '-4.25rem', fall: '2rem', gravity: '7rem', drift: '1.4rem', endDrift: '2rem', delay: '140ms', turn: '620deg', color: '#ff9f0a', shape: 'ribbon' },
  { x: '-3rem', y: '-2.5rem', fall: '3rem', gravity: '9rem', drift: '-0.6rem', endDrift: '-1rem', delay: '210ms', turn: '-520deg', color: '#30d158', shape: 'dot' },
  { x: '-1.5rem', y: '-5rem', fall: '2rem', gravity: '7.5rem', drift: '0.9rem', endDrift: '1.4rem', delay: '280ms', turn: '560deg', color: '#bf5af2', shape: 'ribbon' },
  { x: '1rem', y: '-4rem', fall: '2.5rem', gravity: '8rem', drift: '-1.1rem', endDrift: '-1.8rem', delay: '110ms', turn: '-680deg', color: '#0071e3', shape: 'dot' },
  { x: '3rem', y: '-4.75rem', fall: '2rem', gravity: '7rem', drift: '1.2rem', endDrift: '1.8rem', delay: '180ms', turn: '500deg', color: '#ff375f', shape: 'ribbon' },
  { x: '4.5rem', y: '-2.75rem', fall: '3rem', gravity: '9.5rem', drift: '-0.9rem', endDrift: '-1.5rem', delay: '250ms', turn: '-600deg', color: '#ff9f0a', shape: 'dot' },
  { x: '6rem', y: '-4rem', fall: '2.25rem', gravity: '7.5rem', drift: '1.4rem', endDrift: '2.1rem', delay: '320ms', turn: '640deg', color: '#30d158', shape: 'ribbon' },
  { x: '7.5rem', y: '-1.75rem', fall: '2.5rem', gravity: '8.5rem', drift: '-0.7rem', endDrift: '-1.2rem', delay: '390ms', turn: '-470deg', color: '#bf5af2', shape: 'dot' },
  { x: '6.5rem', y: '1rem', fall: '3rem', gravity: '9rem', drift: '1rem', endDrift: '1.6rem', delay: '460ms', turn: '580deg', color: '#0071e3', shape: 'ribbon' },
  { x: '4rem', y: '2rem', fall: '2rem', gravity: '7rem', drift: '-1.2rem', endDrift: '-2rem', delay: '530ms', turn: '-720deg', color: '#ff375f', shape: 'dot' },
  { x: '1.5rem', y: '2.75rem', fall: '2.5rem', gravity: '8rem', drift: '0.8rem', endDrift: '1.3rem', delay: '600ms', turn: '520deg', color: '#ff9f0a', shape: 'ribbon' },
  { x: '-1.5rem', y: '3.5rem', fall: '2rem', gravity: '7.5rem', drift: '-1rem', endDrift: '-1.6rem', delay: '670ms', turn: '-540deg', color: '#30d158', shape: 'dot' },
  { x: '-4rem', y: '2.5rem', fall: '3rem', gravity: '9rem', drift: '1.2rem', endDrift: '1.9rem', delay: '740ms', turn: '680deg', color: '#bf5af2', shape: 'ribbon' },
  { x: '-6.5rem', y: '1rem', fall: '2.25rem', gravity: '8rem', drift: '-0.8rem', endDrift: '-1.3rem', delay: '810ms', turn: '-480deg', color: '#0071e3', shape: 'dot' },
]

const sparkles = [
  { x: '-5rem', y: '-4.75rem', delay: '80ms', size: 'large' },
  { x: '5rem', y: '-4.25rem', delay: '220ms', size: 'small' },
  { x: '6rem', y: '2.5rem', delay: '400ms', size: 'large' },
  { x: '-6rem', y: '2.25rem', delay: '540ms', size: 'small' },
]

export default function SpecialMealCelebration() {
  return (
    <span className="special-meal-particle-field" aria-hidden="true">
      {particles.map((particle, index) => (
        <span
          key={`${particle.x}-${particle.y}-${index}`}
          className={`special-meal-particle ${particle.shape === 'dot' ? 'special-meal-particle-dot' : 'special-meal-particle-ribbon'}`}
          style={{
            left: 0,
            top: 0,
            backgroundColor: particle.color,
            '--special-meal-x': particle.x,
            '--special-meal-y': particle.y,
            '--special-meal-fall': particle.fall,
            '--special-meal-gravity': particle.gravity,
            '--special-meal-drift': particle.drift,
            '--special-meal-end-drift': particle.endDrift,
            '--special-meal-delay': particle.delay,
            '--special-meal-turn': particle.turn,
          }}
        />
      ))}

      {sparkles.map((sparkle, index) => (
        <span
          key={`${sparkle.x}-${sparkle.y}-${index}`}
          className={`special-meal-sparkle ${sparkle.size === 'large' ? 'special-meal-sparkle-large' : 'special-meal-sparkle-small'}`}
          style={{
            left: sparkle.x,
            top: sparkle.y,
            '--special-meal-delay': sparkle.delay,
          }}
        >
          ✦
        </span>
      ))}
    </span>
  )
}
