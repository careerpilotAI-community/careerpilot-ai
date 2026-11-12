import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import Tracker from './Tracker';
import ResumeManager from './ResumeManager';

type CsrfState = {
  headerName: string;
  token: string;
};

function App() {
  const [csrf, setCsrf] = useState<CsrfState | null>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authenticatedEmail, setAuthenticatedEmail] = useState('');
  const [registerMode, setRegisterMode] = useState(false);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function refreshCsrf() {
    const response = await fetch('/api/auth/csrf', {
      credentials: 'include',
    });
    if (!response.ok) {
      throw new Error('Unable to initialize secure session.');
    }
    setCsrf((await response.json()) as CsrfState);
  }

  useEffect(() => {
    void (async () => {
      try {
        await refreshCsrf();
        const response = await fetch('/api/auth/me', {
          credentials: 'include',
        });
        if (response.ok) {
          const account = (await response.json()) as { email: string };
          setAuthenticatedEmail(account.email);
        }
      } catch {
        setMessage(
          'The API is not available yet. Start the backend and try again.',
        );
      }
    })();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!csrf) return;

    setBusy(true);
    setMessage('');

    try {
      const response = await fetch(
        registerMode ? '/api/auth/register' : '/api/auth/login',
        {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({ email, password }),
        },
      );
      const result = (await response.json().catch(() => ({}))) as {
        email?: string;
        message?: string;
      };

      if (!response.ok) {
        throw new Error(result.message ?? 'Authentication failed.');
      }

      if (registerMode) {
        setMessage(
          result.message ??
            'If registration is available, your account can now be used.',
        );
        setRegisterMode(false);
        setPassword('');
      } else {
        setAuthenticatedEmail(result.email ?? email);
        setPassword('');
        setMessage('You are signed in.');
      }
      await refreshCsrf();
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : 'Something went wrong.',
      );
      await refreshCsrf().catch(() => undefined);
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    if (!csrf) return;

    setBusy(true);
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'include',
        headers: { [csrf.headerName]: csrf.token },
      });
      setAuthenticatedEmail('');
      setMessage('You have been signed out.');
      await refreshCsrf();
    } catch {
      setMessage('Unable to complete sign out. Please retry.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className={authenticatedEmail ? 'workspace-shell' : 'page-shell'}>
      {authenticatedEmail && csrf ? (
        <>
          <header className="workspace-header">
            <a className="brand" href="/">
              CAREERPILOT AI
            </a>
            <div>
              <span>Signed in as {authenticatedEmail}</span>
              <button
                type="button"
                className="secondary-button"
                onClick={() => void logout()}
                disabled={busy}
              >
                Sign out
              </button>
            </div>
          </header>
          <Tracker csrf={csrf} />
          <ResumeManager csrf={csrf} />
        </>
      ) : (
        <section className="hero" aria-labelledby="page-title">
          <p className="eyebrow">CAREERPILOT AI</p>
          <h1 id="page-title">
            Your career journey, <span>with a co-pilot.</span>
          </h1>
          <p className="intro">
            A private workspace for organizing opportunities, preparing
            applications, and building interview confidence — with you in
            control.
          </p>
          <form
            className="status-card auth-form"
            onSubmit={(event) => void submit(event)}
          >
            <div>
              <strong>
                {registerMode
                  ? 'Create your account'
                  : 'Sign in to your workspace'}
              </strong>
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                maxLength={320}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                autoComplete={
                  registerMode ? 'new-password' : 'current-password'
                }
                required
                minLength={registerMode ? 12 : 1}
                maxLength={128}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <button type="submit" disabled={busy || !csrf}>
                {busy
                  ? 'Please wait…'
                  : registerMode
                    ? 'Create account'
                    : 'Sign in'}
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={() => {
                  setRegisterMode(!registerMode);
                  setMessage('');
                }}
              >
                {registerMode
                  ? 'Already have an account? Sign in'
                  : 'New here? Create an account'}
              </button>
            </div>
          </form>
          {message && (
            <p role="status" className="footnote">
              {message}
            </p>
          )}
          <p className="footnote">
            AI suggestions will always be yours to review and approve.
          </p>
        </section>
      )}
    </main>
  );
}

export default App;
