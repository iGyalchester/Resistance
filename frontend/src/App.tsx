import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/AuthContext';
import DashboardPage from './pages/DashboardPage';
import LoginCodePage from './pages/LoginCodePage';
import LoginEmailPage from './pages/LoginEmailPage';

/**
 * Route table only - main.tsx supplies the BrowserRouter and
 * AuthProvider, tests supply a MemoryRouter instead.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginEmailPage />} />
      <Route path="/login/code" element={<LoginCodePage />} />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
