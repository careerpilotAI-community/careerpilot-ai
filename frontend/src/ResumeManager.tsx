import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';

type Csrf = {
  headerName: string;
  token: string;
};

type ResumeAsset = {
  id: string;
  assetKind: 'ORIGINAL' | 'TAILORED';
  filename: string;
  mediaType: string;
  sizeBytes: number;
  checksumSha256: string;
  status: string;
  createdAt: string;
};

export default function ResumeManager({ csrf }: { csrf: Csrf }) {
  const [assets, setAssets] = useState<ResumeAsset[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [kind, setKind] = useState<'ORIGINAL' | 'TAILORED'>('ORIGINAL');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  const refresh = useCallback(async () => {
    const response = await fetch('/api/resumes', { credentials: 'include' });
    if (!response.ok) {
      throw new Error('Unable to load resume documents.');
    }
    setAssets((await response.json()) as ResumeAsset[]);
  }, []);

  useEffect(() => {
    let active = true;
    void fetch('/api/resumes', { credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error('Unable to load resume documents.');
        }
        return (await response.json()) as ResumeAsset[];
      })
      .then((data) => {
        if (active) setAssets(data);
      })
      .catch((error: unknown) => {
        if (active) {
          setMessage(
            error instanceof Error
              ? error.message
              : 'Unable to load documents.',
          );
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return;
    setBusy(true);
    setMessage('');
    try {
      const body = new FormData();
      body.append('file', file);
      const response = await fetch(`/api/resumes?kind=${kind}`, {
        method: 'POST',
        credentials: 'include',
        headers: { [csrf.headerName]: csrf.token },
        body,
      });
      if (!response.ok) {
        const payload = (await response.json().catch(() => ({}))) as {
          detail?: string;
          message?: string;
        };
        throw new Error(
          payload.detail ??
            payload.message ??
            `Upload failed (${response.status}).`,
        );
      }
      setFile(null);
      const input = document.getElementById(
        'resume-file',
      ) as HTMLInputElement | null;
      if (input) input.value = '';
      setMessage('Document uploaded and passed malware scanning.');
      await refresh();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Upload failed.');
    } finally {
      setBusy(false);
    }
  }

  async function download(asset: ResumeAsset) {
    setBusy(true);
    setMessage('');
    try {
      const response = await fetch(`/api/resumes/${asset.id}/download`, {
        credentials: 'include',
      });
      if (!response.ok) {
        throw new Error('Unable to download this document.');
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = asset.filename;
      anchor.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Download failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section
      className="resume-manager tracker-card"
      aria-label="Resume documents"
    >
      <div className="resume-heading">
        <div>
          <h3>Resume documents</h3>
          <p className="muted">
            Private PDF and DOCX files. Originals and tailored copies are kept
            as separate immutable assets.
          </p>
        </div>
        <span className="resume-quota">
          10 MiB per file · 100 MiB per account
        </span>
      </div>
      <form className="resume-upload" onSubmit={(event) => void upload(event)}>
        <label htmlFor="resume-kind">
          Document type
          <select
            id="resume-kind"
            value={kind}
            onChange={(event) =>
              setKind(event.target.value as 'ORIGINAL' | 'TAILORED')
            }
          >
            <option value="ORIGINAL">Original resume</option>
            <option value="TAILORED">Tailored resume</option>
          </select>
        </label>
        <label htmlFor="resume-file">
          Choose PDF or DOCX
          <input
            id="resume-file"
            type="file"
            accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            required
          />
        </label>
        <button disabled={busy || !file}>
          {busy ? 'Please wait…' : 'Upload securely'}
        </button>
      </form>
      {message && (
        <p className="feedback" role="status">
          {message}
        </p>
      )}
      <ul className="resume-list">
        {assets.map((asset) => (
          <li key={asset.id}>
            <div>
              <strong>{asset.filename}</strong>
              <span>
                {asset.assetKind === 'ORIGINAL' ? 'Original' : 'Tailored'} ·{' '}
                {(asset.sizeBytes / 1024).toFixed(0)} KB ·{' '}
                {new Date(asset.createdAt).toLocaleDateString()}
              </span>
              <small>SHA-256: {asset.checksumSha256}</small>
            </div>
            <button
              type="button"
              className="secondary-button"
              disabled={busy || asset.status !== 'AVAILABLE'}
              onClick={() => void download(asset)}
            >
              Download
            </button>
          </li>
        ))}
        {assets.length === 0 && (
          <li className="muted">No resume documents uploaded yet.</li>
        )}
      </ul>
    </section>
  );
}
