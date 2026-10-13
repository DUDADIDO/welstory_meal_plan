import { useEffect, useState } from 'react'
import { nearestRotation } from '../../utils/moonTilt'
import { moonLitPath } from '../../utils/lunarMoon'

// Layered lunar seas, crater rims and surface grain rather than an icon face.
export default function WeatherMoon({ lunar, tilt = 0 }) {
  const [rotation, setRotation] = useState(tilt)
  useEffect(() => { setRotation(previous => nearestRotation(previous, tilt)) }, [tilt])
  return <svg viewBox="0 0 100 100" className="weather-moon-surface" data-lunar-day={lunar?.day} data-phase={lunar?.phase} data-tilt={tilt} style={{ transform: `rotate(${rotation}deg)` }} aria-hidden="true">
    <defs>
      <radialGradient id="lunar-base" cx="35%" cy="30%" r="75%"><stop stopColor="#f3f0df" /><stop offset=".6" stopColor="#c9c9c1" /><stop offset="1" stopColor="#7d8388" /></radialGradient>
      <radialGradient id="lunar-crater"><stop stopColor="#858b8b" /><stop offset=".65" stopColor="#aaaead" /><stop offset=".8" stopColor="#e5e3d7" /><stop offset="1" stopColor="#a4aaa6" stopOpacity="0" /></radialGradient>
      <linearGradient id="lunar-shadow"><stop offset=".45" stopColor="#101b30" stopOpacity="0" /><stop offset=".78" stopColor="#101b30" stopOpacity=".15" /><stop offset="1" stopColor="#050d1c" stopOpacity=".24" /></linearGradient>
      <clipPath id="lunar-lit"><path d={moonLitPath(lunar?.phase ?? 0)} /></clipPath>
      <clipPath id="lunar-disc"><circle cx="50" cy="50" r="48" /></clipPath>
      <filter id="lunar-grain"><feTurbulence type="fractalNoise" baseFrequency=".42" numOctaves="3" seed="13" /><feColorMatrix type="saturate" values="0" /><feComposite in2="SourceGraphic" operator="in" /><feBlend in="SourceGraphic" mode="multiply" /></filter>
    </defs>
    <g clipPath="url(#lunar-disc)">
      <circle cx="50" cy="50" r="48" fill="#b7c3d6" opacity=".05" />
      <g clipPath="url(#lunar-lit)">
      <circle cx="50" cy="50" r="48" fill="url(#lunar-base)" />
      <g fill="#727e80" opacity=".4" filter="url(#lunar-grain)">
        <path d="M18 26Q27 8 42 16T51 34Q46 43 30 39T18 26M49 20Q65 9 76 29T68 49Q50 50 49 36ZM22 46Q39 35 47 55T38 73Q15 70 22 46M54 48Q70 39 81 53T72 72Q51 76 54 48" />
      </g>
      <g fill="url(#lunar-crater)">
        {[[30,23,6],[57,18,4],[22,56,5],[61,72,10],[74,36,5],[40,78,5],[82,63,4],[46,46,3],[34,62,3],[65,53,4],[19,37,3],[52,85,3],[75,79,4],[43,29,3]].map(([cx,cy,r], i) => <circle key={i} cx={cx} cy={cy} r={r} />)}
      </g>
      <circle cx="50" cy="50" r="48" fill="#d4d2c4" opacity=".16" filter="url(#lunar-grain)" />
      <circle cx="50" cy="50" r="48" fill="url(#lunar-shadow)" />
      </g>
    </g>
  </svg>
}
