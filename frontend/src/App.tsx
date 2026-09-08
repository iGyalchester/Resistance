import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth } from './auth/AuthContext';
import AppShell from './components/AppShell';
import ApplicationDetailPage from './pages/ApplicationDetailPage';
import AdminPage from './pages/AdminPage';
import ApplicationsPage from './pages/ApplicationsPage';
import AssistantPage from './pages/AssistantPage';
import ContactsPage from './pages/ContactsPage';
import DashboardPage from './pages/DashboardPage';
import HelpPage from './pages/HelpPage';
import LoginCodePage from './pages/LoginCodePage';
import LoginEmailPage from './pages/LoginEmailPage';
import ProfilePage from './pages/ProfilePage';

/**
 * Route table only - main.tsx supplies the BrowserRouter and
 * AuthProvider, tests supply a MemoryRouter instead. Everything under
 * the AppShell layout route requires a signed-in session.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginEmailPage />} />
      <Route path="/login/code" element={<LoginCodePage />} />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/applications" element={<ApplicationsPage />} />
        <Route path="/applications/:id" element={<ApplicationDetailPage />} />
        <Route path="/contacts" element={<ContactsPage />} />
        <Route path="/assistant" element={<AssistantPage />} />
        <Route path="/help" element={<HelpPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
