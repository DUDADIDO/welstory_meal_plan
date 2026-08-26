import { Outlet } from 'react-router-dom'
import Brand from '../components/common/Brand'
import ThemeToggle from '../components/common/ThemeToggle'

export default function AdminLayout() {
  return (
    <div className="min-h-dvh bg-canvas text-ink antialiased">
      <header className="border-b border-white/10 bg-[#111] text-white">
        <div className="mx-auto flex h-13 max-w-7xl items-center justify-between px-5 sm:px-8">
          <Brand to="/" inverse />
          <div className="flex items-center gap-3">
            <span className="text-xs font-semibold tracking-[0.16em] text-white/55">ADMIN CONSOLE</span>
            <ThemeToggle inverse />
          </div>
        </div>
      </header>
      <Outlet />
    </div>
  )
}
