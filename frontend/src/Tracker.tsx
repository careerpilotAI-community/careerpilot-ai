import { useEffect, useState } from 'react';

type Csrf = {
  headerName: string;
  token: string;
};

type Job = {
  id: string;
  title: string;
  companyName: string;
  companyWebsite: string | null;
  location: string | null;
  employmentType: string | null;
  workMode: string | null;
  description: string | null;
  sourceUrl: string | null;
  sourceName: string | null;
  postedAt: string | null;
  closingAt: string | null;
};

type Skill = {
  id: string;
  skillName: string;
  proficiency: string | null;
  yearsExperience: number | null;
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
  const [editingSkillId, setEditingSkillId] = useState('');
  const [editingSkillName, setEditingSkillName] = useState('');
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [location, setLocation] = useState('');
  const [selectedJob, setSelectedJob] = useState('');
  const [editingJobId, setEditingJobId] = useState('');
  const [editingJobTitle, setEditingJobTitle] = useState('');
  const [editingCompany, setEditingCompany] = useState('');
  const [editingLocation, setEditingLocation] = useState('');
  const [editingApplicationId, setEditingApplicationId] = useState('');
  const [applicationForm, setApplicationForm] = useState({
    appliedDate: '',
    notes: '',
    nextActionDate: '',
    interviewAt: '',
    interviewTimezone: '',
  });
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
                {editingSkillId === skill.id ? (
                  <>
                    <input
                      aria-label={`Edit ${skill.skillName}`}
                      value={editingSkillName}
                      onChange={(event) =>
                        setEditingSkillName(event.target.value)
                      }
                    />
                    <div className="record-actions">
                      <button
                        disabled={busy || !editingSkillName.trim()}
                        onClick={() =>
                          void run(
                            () =>
                              api(`/api/profile/skills/${skill.id}`, 'PUT', {
                                skillName: editingSkillName,
                                proficiency: skill.proficiency,
                                yearsExperience: skill.yearsExperience,
                              }),
                            'Skill updated.',
                          ).then(() => setEditingSkillId(''))
                        }
                      >
                        Save
                      </button>
                      <button
                        className="secondary-button"
                        onClick={() => setEditingSkillId('')}
                      >
                        Cancel
                      </button>
                    </div>
                  </>
                ) : (
                  <>
                    <span>{skill.skillName}</span>
                    <div className="record-actions">
                      <button
                        className="text-button"
                        onClick={() => {
                          setEditingSkillId(skill.id);
                          setEditingSkillName(skill.skillName);
                        }}
                      >
                        Edit
                      </button>
                      <button
                        className="text-button"
                        onClick={() =>
                          void run(
                            () =>
                              api(`/api/profile/skills/${skill.id}`, 'DELETE'),
                            'Skill removed.',
                          )
                        }
                      >
                        Remove
                      </button>
                    </div>
                  </>
                )}
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
                {editingJobId === job.id ? (
                  <div className="job-edit-fields">
                    <input
                      aria-label="Edit job title"
                      value={editingJobTitle}
                      onChange={(event) =>
                        setEditingJobTitle(event.target.value)
                      }
                    />
                    <input
                      aria-label="Edit company"
                      value={editingCompany}
                      onChange={(event) =>
                        setEditingCompany(event.target.value)
                      }
                    />
                    <input
                      aria-label="Edit job location"
                      value={editingLocation}
                      onChange={(event) =>
                        setEditingLocation(event.target.value)
                      }
                    />
                    <div className="record-actions">
                      <button
                        disabled={
                          busy ||
                          !editingJobTitle.trim() ||
                          !editingCompany.trim()
                        }
                        onClick={() =>
                          void run(
                            () =>
                              api(`/api/jobs/${job.id}`, 'PUT', {
                                title: editingJobTitle,
                                companyName: editingCompany,
                                companyWebsite: job.companyWebsite,
                                location: editingLocation || null,
                                employmentType: job.employmentType,
                                workMode: job.workMode,
                                description: job.description,
                                sourceUrl: job.sourceUrl,
                                sourceName: job.sourceName,
                                postedAt: job.postedAt,
                                closingAt: job.closingAt,
                              }),
                            'Job updated.',
                          ).then(() => setEditingJobId(''))
                        }
                      >
                        Save
                      </button>
                      <button
                        className="secondary-button"
                        onClick={() => setEditingJobId('')}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div>
                      <strong>{job.title}</strong>
                      <span>
                        {job.companyName}
                        {job.location ? ` · ${job.location}` : ''}
                      </span>
                    </div>
                    <div className="record-actions">
                      <button
                        className="text-button"
                        onClick={() => {
                          setEditingJobId(job.id);
                          setEditingJobTitle(job.title);
                          setEditingCompany(job.companyName);
                          setEditingLocation(job.location ?? '');
                        }}
                      >
                        Edit
                      </button>
                      <button
                        className="text-button"
                        onClick={() => setSelectedJob(job.id)}
                      >
                        Track
                      </button>
                      <button
                        className="text-button"
                        onClick={() =>
                          void run(
                            () => api(`/api/jobs/${job.id}`, 'DELETE'),
                            'Job deleted.',
                          )
                        }
                      >
                        Delete
                      </button>
                    </div>
                  </>
                )}
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
                  <button
                    className="text-button"
                    onClick={() => {
                      setEditingApplicationId(application.id);
                      setApplicationForm({
                        appliedDate: application.appliedDate ?? '',
                        notes: application.notes ?? '',
                        nextActionDate: application.nextActionDate ?? '',
                        interviewAt: application.interviewAt
                          ? new Date(
                              new Date(application.interviewAt).getTime() -
                                new Date(
                                  application.interviewAt,
                                ).getTimezoneOffset() *
                                  60000,
                            )
                              .toISOString()
                              .slice(0, 16)
                          : '',
                        interviewTimezone:
                          application.interviewTimezone ??
                          Intl.DateTimeFormat().resolvedOptions().timeZone,
                      });
                    }}
                  >
                    Edit details
                  </button>
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
                {editingApplicationId === application.id && (
                  <form
                    className="application-edit-form"
                    onSubmit={(event) => {
                      event.preventDefault();
                      const interviewAt = applicationForm.interviewAt
                        ? new Date(applicationForm.interviewAt).toISOString()
                        : null;
                      void run(
                        () =>
                          api(`/api/applications/${application.id}`, 'PUT', {
                            jobId: application.jobId,
                            status: application.status,
                            appliedDate: applicationForm.appliedDate || null,
                            notes: applicationForm.notes || null,
                            nextActionDate:
                              applicationForm.nextActionDate || null,
                            interviewAt,
                            interviewTimezone: interviewAt
                              ? applicationForm.interviewTimezone
                              : null,
                          }),
                        'Application details updated.',
                      ).then(() => setEditingApplicationId(''));
                    }}
                  >
                    <label>
                      Applied date
                      <input
                        type="date"
                        value={applicationForm.appliedDate}
                        onChange={(event) =>
                          setApplicationForm({
                            ...applicationForm,
                            appliedDate: event.target.value,
                          })
                        }
                      />
                    </label>
                    <label>
                      Next action date
                      <input
                        type="date"
                        value={applicationForm.nextActionDate}
                        onChange={(event) =>
                          setApplicationForm({
                            ...applicationForm,
                            nextActionDate: event.target.value,
                          })
                        }
                      />
                    </label>
                    <label>
                      Interview date and time
                      <input
                        type="datetime-local"
                        value={applicationForm.interviewAt}
                        onChange={(event) =>
                          setApplicationForm({
                            ...applicationForm,
                            interviewAt: event.target.value,
                          })
                        }
                      />
                    </label>
                    <label>
                      Interview timezone
                      <input
                        value={applicationForm.interviewTimezone}
                        onChange={(event) =>
                          setApplicationForm({
                            ...applicationForm,
                            interviewTimezone: event.target.value,
                          })
                        }
                        maxLength={64}
                      />
                    </label>
                    <label className="notes-field">
                      Notes
                      <textarea
                        rows={3}
                        maxLength={10000}
                        value={applicationForm.notes}
                        onChange={(event) =>
                          setApplicationForm({
                            ...applicationForm,
                            notes: event.target.value,
                          })
                        }
                      />
                    </label>
                    <div className="record-actions">
                      <button disabled={busy}>Save details</button>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => setEditingApplicationId('')}
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                )}
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
