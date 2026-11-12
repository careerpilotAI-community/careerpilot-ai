function App() {
  return (
    <main className="page-shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">CAREERPILOT AI</p>
        <h1 id="page-title">
          Your career journey, <span>with a co-pilot.</span>
        </h1>
        <p className="intro">
          A thoughtful workspace for discovering opportunities, preparing
          applications, and building interview confidence — with you in control.
        </p>
        <div className="status-card">
          <span className="status-dot" aria-hidden="true" />
          <div>
            <strong>Application foundation is ready</strong>
            <p>
              Profile, job tracking, and career tools are coming in planned
              stages.
            </p>
          </div>
        </div>
        <p className="footnote">
          AI suggestions will always be yours to review and approve.
        </p>
      </section>
    </main>
  );
}

export default App;
