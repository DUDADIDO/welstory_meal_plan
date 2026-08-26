import { Link } from 'react-router-dom'
import MarkIcon from '../../assets/icons/MarkIcon'

export default function Brand({ to = '/', inverse = false }) {
  return (
    <Link to={to} className={`flex items-center gap-2 text-sm font-bold tracking-tight ${inverse ? 'text-white' : 'text-ink'}`}>
      <MarkIcon className="size-6" />
      <span>오늘 뭐먹지</span>
    </Link>
  )
}
