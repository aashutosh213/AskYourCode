export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-5xl flex-col px-6 py-16">
      <header className="mb-10">
        <p className="text-sm uppercase tracking-[0.2em] text-sky-400">AskYourCode</p>
        <h1 className="mt-3 text-4xl font-bold text-white">Repository search foundation</h1>
      </header>

      <section className="rounded-2xl border border-slate-700 bg-slate-900 p-6 shadow-xl shadow-slate-950/30">
        <div className="flex flex-col gap-5 md:flex-row">
          <input
            className="flex-1 rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-slate-100 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500"
            placeholder="Ask anything about your codebase"
            defaultValue="Where is JWT authentication implemented?"
          />
          <button className="rounded-xl bg-sky-600 px-5 py-3 font-medium text-white transition hover:bg-sky-500">
            Search
          </button>
        </div>
      </section>

      <section className="mt-8 space-y-4">
        <div className="rounded-xl border border-slate-700 bg-slate-900 p-4">
          <p className="text-sm text-slate-400">Current status</p>
          <p className="mt-2 text-lg font-medium text-slate-200">Phase 0 / Phase 1 foundation is in progress.</p>
        </div>
      </section>
    </main>
  );
}
