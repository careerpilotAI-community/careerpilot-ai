import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the CareerPilot AI sign-in screen', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (url: string) => {
        if (url === '/api/auth/csrf') {
          return {
            ok: true,
            json: async () => ({
              headerName: 'X-XSRF-TOKEN',
              token: 'test-token',
            }),
          };
        }
        return { ok: false };
      }),
    );

    render(<App />);

    expect(
      screen.getByRole('heading', {
        name: /your career journey, with a co-pilot\./i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/sign in to your workspace/i),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    });
  });
});
