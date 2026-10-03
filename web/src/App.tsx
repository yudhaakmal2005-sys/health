import { lazy, Suspense, useEffect, type ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { ToastProvider } from './components/Toast'
import { Spinner } from './components/ui'
import { LandingPage } from './pages/Landing'
import { PrivacyPage } from './pages/Privacy'
import { LoginPage } from './pages/Login'
import { NotFoundPage } from './pages/NotFound'

const DashboardLayout = lazy(() => import('./pages/dashboard/Layout'))
const OverviewPage = lazy(() => import('./pages/dashboard/Overview'))
const RwMapPage = lazy(() => import('./pages/dashboard/RwMap'))
const FollowUpsPage = lazy(() => import('./pages/dashboard/FollowUps'))
const CadresPage = lazy(() => import('./pages/dashboard/Cadres'))
const PosyanduPage = lazy(() => import('./pages/dashboard/Posyandu'))
const ThresholdsPage = lazy(() => import('./pages/dashboard/Thresholds'))
const ReportsPage = lazy(() => import('./pages/dashboard/Reports'))
const AuditPage = lazy(() => import('./pages/dashboard/Audit'))
const AiUsagePage = lazy(() => import('./pages/dashboard/AiUsage'))

function ScrollToTop() {
  const { pathname, hash } = useLocation()
  useEffect(() => {
    if (hash) {
      document.getElementById(hash.slice(1))?.scrollIntoView()
      return
    }
    window.scrollTo(0, 0)
  }, [pathname, hash])
  return null
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { session } = useAuth()
  const loc = useLocation()
  if (!session || session.user.role !== 'ADMIN') return <Navigate to="/masuk" replace state={{ from: loc.pathname }} />
  return <>{children}</>
}

export function PageFallback() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center text-muted" role="status">
      <Spinner size={22} />
      <span className="sr-only">Memuat halaman…</span>
    </div>
  )
}

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <ScrollToTop />
        <Suspense fallback={<PageFallback />}>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/privasi" element={<PrivacyPage />} />
            <Route path="/masuk" element={<LoginPage />} />
            <Route path="/dasbor" element={<RequireAdmin><DashboardLayout /></RequireAdmin>}>
              <Route index element={<OverviewPage />} />
              <Route path="peta-rw" element={<RwMapPage />} />
              <Route path="tindak-lanjut" element={<FollowUpsPage />} />
              <Route path="kader" element={<CadresPage />} />
              <Route path="jadwal" element={<PosyanduPage />} />
              <Route path="ambang" element={<ThresholdsPage />} />
              <Route path="laporan" element={<ReportsPage />} />
              <Route path="audit" element={<AuditPage />} />
              <Route path="ai" element={<AiUsagePage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </AuthProvider>
    </ToastProvider>
  )
}
