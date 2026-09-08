import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { logout } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ToastProvider } from './Toast';

/**
 * The frame around every signed-in page: brand, main navigation, who is
 * logged in, log out. The Admin link exists only for accounts the server
 * reported as ADMIN; the server enforces it too, this just avoids a dead
 * link for everyone else.
 */
export default function AppShell() {
  const { me, setMe } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    try {
      await logout();
    } finally {
      setMe(null);
      navigate('/login', { replace: true });
    }
  }

  return (
    <ToastProvider>
      <div className="shell">
        <header className="topbar">
          <Link to="/dashboard" className="brand">
            Resistance
          </Link>
          <nav aria-label="Main">
            <NavLink to="/dashboard">Dashboard</NavLink>
            <NavLink to="/applications">Applications</NavLink>
            <NavLink to="/contacts">Contacts</NavLink>
            <NavLink to="/profile">Profile</NavLink>
            {me?.roles?.includes('ADMIN') && <NavLink to="/admin">Admin</NavLink>}
          </nav>
          <div className="topbar-right">
            <span className="muted">{me?.fullName}</span>
            <button className="link" onClick={onLogout}>
              Log out
            </button>
          </div>
        </header>
        <main className="page">
          <Outlet />
        </main>
      </div>
    </ToastProvider>
  );
}
