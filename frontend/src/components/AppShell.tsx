import { useCallback, useMemo, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { logout } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AssistantDrawer from './assistant/AssistantDrawer';
import { AssistantDrawerContext } from './assistant/AssistantDrawerContext';
import { ToastProvider } from './Toast';

/**
 * The frame around every signed-in page: brand, main navigation, who is
 * logged in, log out. The Admin link exists only for accounts the server
 * reported as ADMIN; the server enforces it too, this just avoids a dead
 * link for everyone else. The assistant drawer lives here so any page can
 * open it (the dashboard does) and the chat survives navigation.
 */
export default function AppShell() {
  const { me, setMe } = useAuth();
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [prefill, setPrefill] = useState<string | undefined>(undefined);
  const assistantEnabled = me?.features?.assistant === true;

  const open = useCallback((text?: string) => {
    setPrefill(text);
    setDrawerOpen(true);
  }, []);
  const close = useCallback(() => setDrawerOpen(false), []);
  const drawer = useMemo(() => ({ open, close }), [open, close]);

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
      <AssistantDrawerContext.Provider value={drawer}>
      <div className="shell">
        <header className="topbar">
          <Link to="/dashboard" className="brand">
            Resistance
          </Link>
          <nav aria-label="Main">
            <NavLink to="/dashboard">Dashboard</NavLink>
            <NavLink to="/applications">Applications</NavLink>
            <NavLink to="/contacts">Contacts</NavLink>
            <NavLink to="/assistant">Assistant</NavLink>
            <NavLink to="/help">Help</NavLink>
            <NavLink to="/profile">Profile</NavLink>
            {me?.roles?.includes('ADMIN') && <NavLink to="/admin">Admin</NavLink>}
          </nav>
          <div className="topbar-right">
            {assistantEnabled && (
              <button type="button" className="btn btn-ghost" onClick={() => open()}>
                Ask
              </button>
            )}
            <span className="muted">{me?.fullName}</span>
            <button className="link" onClick={onLogout}>
              Log out
            </button>
          </div>
        </header>
        <main className="page">
          <Outlet />
        </main>
        {assistantEnabled && <AssistantDrawer open={drawerOpen} prefill={prefill} onClose={close} />}
      </div>
      </AssistantDrawerContext.Provider>
    </ToastProvider>
  );
}
