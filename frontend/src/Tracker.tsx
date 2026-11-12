import { useEffect, useState } from 'react';

type Csrf = {
  headerName: string;
  token: string;
};

type Job = {
  id: string;
  title: string;
  companyName: string;
  location: string | null;
};

type Skill = {
  id: string;
  skillName: string;
};

type AppRecord = {
  id: string;
  jobId: string;
  jobTitle: string;
  companyName: string;
  status: string;
  appliedDate: string | null;
  notes: string | null;
  nextActionDate: string | null;
  interviewAt: string | null;
  interviewTimezone: string | null;
};

type Profile = {
  displayName: string | null;
  professionalSummary: string | null;
  experienceYears: number | null;
};

type Summary = {
  totalJobs: number;
  totalApplications: number;
  applicationsByStatus: Record<string, number>;
  upcomingInterviews: {
    applicationId: string;
    jobTitle: string;
    companyName: string;
    interviewAt: string;
  }[];
};

const statuses = [
  'SAVED',
  'PREPARING',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
  'CLOSED',
];

export default function Tracker({ csrf }: { csrf: Csrf }) {
  const [profile, setProfile] = useState({
    displayName: '',
    professionalSummary: '',
    experienceYears: '',
  });
  const [skills, setSkills] = useState<Skill[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [apps, setApps] = useState<AppRecord[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [skillName, setSkillName] = useState('');
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [selectedJob, setSelectedJob] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  async function api<T>(
    path: string,
    method = 'GET',
    body?: unknown,
  ): Promise<T> {
    const response = await fetch(path, {
      method,
      credentials: 'include',
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(method !== 'GET' ? { [csrf.headerName]: csrf.token } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });

    if (!response.ok) {
      const payload = (await response.json().catch(() => ({}))) as {
        detail?: string;
        message?: string;
      };
      throw new Error(
        payload.detail ??
          payload.message ??
          `Request failed (${response.status})`,
      );
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }

  async function load() {
    try {
      const [profileData, skillData, jobData, applicationData, dashboardData] =
        await Promise.all([
          api<Profile>('/api/profile'),
          api<Skill[]>('/api/profile/skills'),
          api<Job[]>('/api/jobs'),
          api<AppRecord[]>('/api/applications'),
          api<Summary>('/api/dashboard/summary'),
        ]);

      setProfile({
        displayName: profileData.displayName ?? '',
        professionalSummary: profileData.professionalSummary ?? '',
        experienceYears: profileData.experienceYears?.toString() ?? '',
      });
      setSkills(skillData);
      setJobs(jobData);
      setApps(applicationData);
      setSummary(dashboardData);
      setError('');
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : 'Unable to load workspace.',
      );
    }
  }

  useEffect(() => {
    void (async () => {
      await load();
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function run(action: () => Promise<unknown>, successMessage: string) {
    setBusy(true);
    setError('');
    setNotice('');

    try {
      await action();
      setNotice(successMessage);
      await load();
    } catch (actionError) {
      setError(
        actionError instanceof Error
          ? actionError.message
          : 'Something went wrong.',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="tracker-shell" aria-label="Career workspace">
      <div className="tracker-heading">
        <div>
          <p className="eyebrow">YOUR WORKSPACE</p>
          <h2>Career tracker</h2>
          <p className="tracker-subtitle">
            Manage your profile, saved opportunities and application progress.
          </p>
        </div>
        <button className="secondary-button" onClick={() => void load()}>
          Refresh
        </button>
      </div>

      {error && (
        <p className="feedback error" role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className="feedback success" role="status">
          {notice}
        </p>
      )}

      <div className="summary-grid">
        <article className="summary-card">
          <span>Saved jobs</span>
          <strong>{summary?.totalJobs ?? 0}</strong>
        </article>
        <article className="summary-card">
          <span>Applications</span>
          <strong>{summary?.totalApplications ?? 0}</strong>
        </article>
        <article className="summary-card">
          <span>Interviews</span>
          <strong>{summary?.applicationsByStatus?.INTERVIEWING ?? 0}</strong>
        </article>
      </div>

      <div className="tracker-grid">
        <section className="tracker-card">
          <h3>Career profile</h3>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void run(
                () =>
                  api('/api/profile', 'PUT', {
                    ...profile,
                    experienceYears: profile.experienceYears
                      ? Number(profile.experienceYears)
                      : null,
                  }),
                'Profile saved.',
              );
            }}
          >
            <label>
              Display name
              <input
                maxLength={160}
                value={profile.displayName}
                onChange={(event) =>
                  setProfile({ ...profile, displayName: event.target.value })
                }
              />
            </label>
            <label>
              Professional summary
              <textarea
                rows={4}
                maxLength={10000}
                value={profile.professionalSummary}
                onChange={(event) =>
                  setProfile({
                    ...profile,
                    professionalSummary: event.target.value,
                  })
                }
              />
            </label>
            <label>
              Years of experience
              <input
                type="number"
                min="0"
                max="80"
                step="0.5"
                value={profile.experienceYears}
                onChange={(event) =>
                  setProfile({
                    ...profile,
                    experienceYears: event.target.value,
                  })
                }
              />
            </label>
            <button disabled={busy}>Save profile</button>
          </form>

          <h3 className="subheading">Skills</h3>
          <form
            className="inline-form"
            onSubmit={(event) => {
              event.preventDefault();
              void run(
                () =>
                  api('/api/profile/skills', 'POST', {
                    skillName,
                    proficiency: null,
                    yearsExperience: null,
                  }),
                'Skill added.',
              ).then(() => setSkillName(''));
            }}
          >
            <input
              aria-label="Skill name"
              placeholder="e.g. Java"
              required
              maxLength={120}
              value={skillName}
              onChange={(event) => setSkillName(event.target.value)}
            />
            <button disabled={busy}>Add</button>
          </form>
          <ul className="skill-list">
            {skills.map((skill) => (
              <li key={skill.id}>
                {skill.skillName}
                <button
                  className="text-button"
                  onClick={() =>
                    void run(
                      () => api(`/api/profile/skills/${skill.id}`, 'DELETE'),
                      'Skill removed.',
                    )
                  }
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="tracker-card">
          <h3>Save a job</h3>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void run(
                () =>
                  api('/api/jobs', 'POST', {
                    title,
                    companyName: company,
                    location: location || null,
                  }),
                'Job saved.',
              ).then(() => {
                setTitle('');
                setCompany('');
                setLocation('');
              });
            }}
          >
            <label>
              Job title
              <input
                required
                maxLength={240}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
              />
            </label>
            <label>
              Company
              <input
                required
                maxLength={240}
                value={company}
                onChange={(event) => setCompany(event.target.value)}
              />
            </label>
            <label>
              Location
              <input
                maxLength={240}
                value={location}
                onChange={(event) => setLocation(event.target.value)}
              />
            </label>
            <button disabled={busy}>Save job</button>
          </form>

          <h3 className="subheading">Saved jobs</h3>
          <ul className="record-list">
            {jobs.map((job) => (
              <li key={job.id}>
                <div>
                  <strong>{job.title}</strong>
                  <span>
                    {job.companyName}
                    {job.location ? ` · ${job.location}` : ''}
                  </span>
                </div>
                <button
                  className="text-button"
                  onClick={() => setSelectedJob(job.id)}
                >
                  Track
                </button>
              </li>
            ))}
          </ul>

          {jobs.length > 0 && (
            <form
              className="inline-form application-create"
              onSubmit={(event) => {
                event.preventDefault();
                void run(
                  () =>
                    api('/api/applications', 'POST', {
                      jobId: selectedJob,
                      status: 'SAVED',
                    }),
                  'Application tracking started.',
                );
              }}
            >
              <select
                aria-label="Select saved job"
                value={selectedJob}
                onChange={(event) => setSelectedJob(event.target.value)}
              >
                <option value="">Choose a job…</option>
                {jobs.map((job) => (
                  <option key={job.id} value={job.id}>
                    {job.title} — {job.companyName}
                  </option>
                ))}
              </select>
              <button disabled={busy || !selectedJob}>Track application</button>
            </form>
          )}
        </section>
      </div>

      <section className="tracker-card application-panel">
        <h3>Applications</h3>
        {apps.length === 0 ? (
          <p className="muted">
            No applications tracked yet. Save a job and start tracking it above.
          </p>
        ) : (
          <div className="application-list">
            {apps.map((application) => (
              <article className="application-row" key={application.id}>
                <div>
                  <strong>{application.jobTitle}</strong>
                  <span>{application.companyName}</span>
                  {application.nextActionDate && (
                    <small>Next action: {application.nextActionDate}</small>
                  )}
                </div>
                <label className="status-select">
                  Status
                  <select
                    value={application.status}
                    onChange={(event) =>
                      void run(
                        () =>
                          api(`/api/applications/${application.id}`, 'PUT', {
                            jobId: application.jobId,
                            status: event.target.value,
                            appliedDate: application.appliedDate,
                            notes: application.notes,
                            nextActionDate: application.nextActionDate,
                            interviewAt: application.interviewAt,
                            interviewTimezone: application.interviewTimezone,
                          }),
                        'Application status updated.',
                      )
                    }
                  >
                    {statuses.map((status) => (
                      <option key={status}>{status}</option>
                    ))}
                  </select>
                </label>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="tracker-card application-panel">
        <h3>Upcoming interviews</h3>
        {summary?.upcomingInterviews.length ? (
          summary.upcomingInterviews.map((interview) => (
            <p key={interview.applicationId}>
              <strong>{interview.jobTitle}</strong> — {interview.companyName} ·{' '}
              {new Date(interview.interviewAt).toLocaleString()}
            </p>
          ))
        ) : (
          <p className="muted">No upcoming interviews recorded.</p>
        )}
      </section>
    </section>
  );
}
