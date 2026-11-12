import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders the CareerPilot AI welcome screen', () => {
    render(<App />);

    expect(
      screen.getByRole('heading', {
        name: /your career journey, with a co-pilot\./i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/application foundation is ready/i),
    ).toBeInTheDocument();
  });
});
