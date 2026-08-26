import { createBrowserRouter } from 'react-router-dom'
import MainLayout from '../layouts/MainLayout'
import AdminLayout from '../layouts/AdminLayout'
import MainPage from '../pages/MainPage'
import AdminPage from '../pages/AdminPage'
import NotFoundPage from '../pages/NotFoundPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    errorElement: <NotFoundPage />,
    children: [{ index: true, element: <MainPage /> }],
  },
  {
    path: '/admin',
    element: <AdminLayout />,
    errorElement: <NotFoundPage />,
    children: [{ index: true, element: <AdminPage /> }],
  },
])
