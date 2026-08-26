import { Outlet } from 'react-router-dom'

export default function MainLayout() {
  return (
    <div className="min-h-dvh bg-canvas text-ink antialiased">
      <Outlet />
    </div>
  )
}
