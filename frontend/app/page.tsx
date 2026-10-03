'use client';

import { FormEvent, useState } from 'react';

type SearchMode = 'reranked' | 'hybrid' | 'vector' | 'keyword';
type RepositoryFile = { relativePath: string; fileName: string; language: string; sizeBytes: number };
type IndexResponse = { repositoryPath: string; status: string; message: string; jobId: string; files: RepositoryFile[] };
type SearchHit = {
  chunkId: number; filePath: string; fileName: string; symbolName: string; symbolType: string;
  content: string; startLine: number; endLine: number; score?: number; retrievalScore?: number;
  rerankScore?: number; keywordMatch?: boolean; vectorMatch?: boolean;
};
type SearchResponse = { results: SearchHit[]; resultsCount: number };
type Citation = { number: number; filePath: string; symbolName: string; startLine: number; endLine: number };
type AskResponse = { query: string; answer: string; citations: Citation[] };
type SourceResponse = { repositoryPath: string; filePath: string; startLine: number; endLine: number; lines: { number: number; content: string; highlighted: boolean }[] };

const modeLabels: Record<SearchMode, string> = {
  reranked: 'Hybrid + reranking', hybrid: 'Hybrid', vector: 'Semantic', keyword: 'Keyword / BM25',
};

async function requestJson<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.details || body.error || body.message || message;
    } catch { /* Keep the HTTP status when the backend did not return JSON. */ }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

function scoreFor(hit: SearchHit): number | undefined {
  return hit.rerankScore ?? hit.score ?? hit.retrievalScore;
}

export default function HomePage() {
  const [repositoryPath, setRepositoryPath] = useState('');
  const [query, setQuery] = useState('Where is JWT authentication implemented?');
  const [mode, setMode] = useState<SearchMode>('reranked');
  const [indexing, setIndexing] = useState(false);
  const [searching, setSearching] = useState(false);
  const [asking, setAsking] = useState(false);
  const [indexResult, setIndexResult] = useState<IndexResponse | null>(null);
  const [searchResult, setSearchResult] = useState<SearchResponse | null>(null);
  const [askResult, setAskResult] = useState<AskResponse | null>(null);
  const [sourceResult, setSourceResult] = useState<SourceResponse | null>(null);
  const [sourceLoading, setSourceLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function indexRepository(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!repositoryPath.trim()) return;
    setIndexing(true); setError(null); setIndexResult(null);
    try {
      const result = await requestJson<IndexResponse>('/api/repositories/index', {
        method: 'POST', body: JSON.stringify({ repositoryPath: repositoryPath.trim() }),
      });
      setRepositoryPath(result.repositoryPath); setIndexResult(result);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Indexing failed.');
    } finally { setIndexing(false); }
  }

  async function searchRepository(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!repositoryPath.trim() || !query.trim()) return;
    setSearching(true); setError(null); setAskResult(null);
    try {
      const result = await requestJson<SearchResponse>(`/api/search/${mode}`, {
        method: 'POST', body: JSON.stringify({ query: query.trim(), repositoryPath: repositoryPath.trim(), limit: 10 }),
      });
      setSearchResult(result);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Search failed.');
    } finally { setSearching(false); }
  }

  async function askAboutRepository() {
    if (!repositoryPath.trim() || !query.trim()) return;
    setAsking(true); setError(null);
    try {
      const result = await requestJson<AskResponse>('/api/ask', {
        method: 'POST', body: JSON.stringify({ query: query.trim(), repositoryPath: repositoryPath.trim(), limit: 5 }),
      });
      setAskResult(result);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Ask request failed.');
    } finally { setAsking(false); }
  }

  async function openSource(citation: Citation) {
    setSourceLoading(true); setError(null);
    try {
      const params = new URLSearchParams({
        repositoryPath: repositoryPath.trim(), fileRelativePath: citation.filePath,
        startLine: String(citation.startLine), endLine: String(citation.endLine),
      });
      setSourceResult(await requestJson<SourceResponse>(`/api/source?${params.toString()}`, { method: 'GET' }));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Source file could not be loaded.');
    } finally { setSourceLoading(false); }
  }

  return (
    <main className="mx-auto min-h-screen max-w-6xl px-5 py-8 sm:px-8 lg:py-12">
      <header className="mb-10 flex flex-col gap-3 border-b border-slate-800 pb-8 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.28em] text-sky-400">AskYourCode</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">Understand your codebase.</h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-400">Index a local repository, retrieve evidence with multiple search strategies, and ask questions with source citations.</p>
        </div>
        <div className="rounded-full border border-emerald-900/80 bg-emerald-950/30 px-3 py-1.5 text-xs text-emerald-300">Local-first RAG</div>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
        <section className="space-y-6">
          <div className="panel">
            <div className="section-kicker">01 / Repository</div>
            <h2 className="section-title">Connect a local repository</h2>
            <p className="section-copy">The backend reads and indexes the directory on the machine where it is running.</p>
            <form className="mt-5 space-y-3" onSubmit={indexRepository}>
              <label className="sr-only" htmlFor="repository-path">Repository path</label>
              <input id="repository-path" className="field" value={repositoryPath} onChange={(event) => setRepositoryPath(event.target.value)} placeholder="/path/to/your/repository" />
              <button className="button-primary w-full" disabled={indexing || !repositoryPath.trim()} type="submit">{indexing ? 'Indexing repository…' : 'Index repository'}</button>
            </form>
            {indexResult && <div className="mt-5 rounded-xl border border-sky-900/70 bg-sky-950/30 p-4 text-sm">
              <div className="flex items-center justify-between gap-3"><span className="font-medium text-sky-200">{indexResult.status}</span><span className="text-slate-400">{indexResult.files?.length ?? 0} files found</span></div>
              <p className="mt-2 leading-5 text-slate-300">{indexResult.message}</p><p className="mt-2 break-all text-xs text-slate-500">Job: {indexResult.jobId}</p>
            </div>}
          </div>

          <div className="panel">
            <div className="section-kicker">02 / Retrieval</div>
            <h2 className="section-title">Search with evidence</h2>
            <p className="section-copy">Try different retrieval strategies to see how exact identifiers and semantic meaning complement each other.</p>
            <form className="mt-5 space-y-4" onSubmit={searchRepository}>
              <label className="sr-only" htmlFor="query">Codebase question</label>
              <textarea id="query" className="field min-h-28 resize-y" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Where is authentication implemented?" />
              <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
                <label className="sr-only" htmlFor="search-mode">Search mode</label>
                <select id="search-mode" className="field" value={mode} onChange={(event) => setMode(event.target.value as SearchMode)}>{Object.entries(modeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
                <button className="button-primary" disabled={searching || !repositoryPath.trim() || !query.trim()} type="submit">{searching ? 'Searching…' : 'Search code'}</button>
              </div>
            </form>
            <button className="button-secondary mt-3 w-full" disabled={asking || !repositoryPath.trim() || !query.trim()} onClick={askAboutRepository} type="button">{asking ? 'Asking local model…' : 'Ask the local model'}</button>
            {!repositoryPath.trim() && <p className="mt-3 text-xs text-amber-300/80">Index a repository before searching or asking a question.</p>}
          </div>
        </section>

        <section className="space-y-6">
          {error && <div className="rounded-xl border border-rose-900/70 bg-rose-950/30 p-4 text-sm leading-5 text-rose-200">{error}</div>}
          {askResult && <div className="panel border-sky-800/70">
            <div className="section-kicker">Answer / Local Ollama</div>
            <div className="mt-4 whitespace-pre-wrap text-[15px] leading-7 text-slate-200">{askResult.answer}</div>
            {askResult.citations.length > 0 && <div className="mt-6 border-t border-slate-800 pt-4"><p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Sources</p><div className="mt-3 space-y-2">{askResult.citations.map((citation) => <button className="citation w-full text-left hover:text-white" key={`${citation.number}-${citation.filePath}-${citation.startLine}`} onClick={() => openSource(citation)} type="button"><span className="citation-number">[{citation.number}]</span><span className="break-all">{citation.filePath}:{citation.startLine}-{citation.endLine}</span>{citation.symbolName && <span className="text-slate-500">{citation.symbolName}</span>}</button>)}</div></div>}
          </div>}

          {sourceResult && <div className="panel border-emerald-900/70"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="section-kicker">Source viewer</div><h2 className="section-title break-all">{sourceResult.filePath}</h2></div><span className="text-xs text-emerald-300">Lines {sourceResult.startLine}-{sourceResult.endLine}</span></div><div className="source-viewer mt-4">{sourceResult.lines.map((line) => <div className={`source-line ${line.highlighted ? 'source-line-highlighted' : ''}`} key={line.number}><span className="source-line-number">{line.number}</span><code>{line.content || ' '}</code></div>)}</div></div>}
          {sourceLoading && <p className="text-xs text-slate-500">Loading cited source…</p>}

          {searchResult ? <div>
            <div className="mb-4 flex items-end justify-between gap-4"><div><div className="section-kicker">Results / {modeLabels[mode]}</div><h2 className="section-title">{searchResult.resultsCount} relevant chunks</h2></div><span className="hidden text-xs text-slate-500 sm:block">{searchResult.resultsCount === 1 ? '1 match' : `${searchResult.resultsCount} matches`}</span></div>
            <div className="space-y-4">{searchResult.results.map((hit, index) => <article className="result-card" key={`${hit.chunkId}-${hit.filePath}`}>
              <div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><p className="break-all text-sm font-medium text-slate-200">{hit.filePath}</p><p className="mt-1 text-xs text-slate-500">{hit.symbolType || 'symbol'} / {hit.symbolName || 'unnamed'} · lines {hit.startLine}-{hit.endLine}</p></div><span className="rounded-full bg-slate-800 px-2.5 py-1 text-xs text-sky-300">#{index + 1} · {scoreFor(hit)?.toFixed(3) ?? '—'}</span></div>
              <pre className="code-block mt-4">{hit.content}</pre><div className="mt-3 flex gap-2 text-[11px] text-slate-500">{hit.keywordMatch && <span className="tag">keyword</span>}{hit.vectorMatch && <span className="tag">semantic</span>}</div>
            </article>)}{searchResult.results.length === 0 && <div className="panel text-sm text-slate-400">No matching chunks were found. Try another mode or query.</div>}</div>
          </div> : <div className="empty-state"><div className="mb-5 text-4xl text-slate-700">⌘</div><h2 className="text-lg font-medium text-slate-300">Your evidence will appear here</h2><p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-slate-500">Index a repository, then search for a symbol, behavior, or architectural concept.</p></div>}
        </section>
      </div>
    </main>
  );
}
