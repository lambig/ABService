import assert from 'node:assert/strict';

// Each entry is also a separately gated CI job. New browser workspaces must register here.
export const browserSuites = {
  e2e: { location: 'e2e', specs: 'src/specs' },
  player: { location: 'packages/player', specs: 'e2e' },
  storage: { location: 'packages/offline-storage', specs: 'e2e' },
  shell: { location: 'packages/offline-shell', specs: 'e2e' },
  'offline-player': { location: 'packages/offline-player', specs: 'e2e' },
  'audio-worklet': { location: 'packages/audio-worklet', specs: 'e2e' },
  visualizer: { location: 'packages/visualizer', specs: 'e2e' },
};
export const browserJobs = Object.fromEntries(Object.keys(browserSuites).filter((id) => id !== 'e2e')
  .map((id) => [`${id}-browser`, `browser_${id.replaceAll('-', '_')}`]));
export const browserKeys = ['browser_e2e', ...Object.values(browserJobs)];
export const browserSelection = ({ event, ref, files, workspaces, exists = () => true }) => {
  const plan = Object.fromEntries(Object.keys(browserSuites).map((id) => [id, []]));
  const all = (ids = Object.keys(plan)) => ids.forEach((id) => { plan[id] = 'all'; });
  const locations = Object.values(browserSuites).map((suite) => suite.location);
  workspaces.filter((w) => w.scripts?.['test:browser']).forEach((w) =>
    assert.ok(locations.includes(w.location), `Register browser workspace: ${w.location}`));
  if (event === 'workflow_dispatch' || (event === 'push' && ref.startsWith('refs/heads/release/'))
    || !['push', 'pull_request'].includes(event) || files.length === 0) {
    all(); return plan;
  }
  const main = event === 'push' && ref === 'refs/heads/main';
  const dependencies = (w) => Object.keys({ ...w.dependencies, ...w.devDependencies,
    ...w.peerDependencies, ...w.optionalDependencies });
  const consumers = (location) => Object.entries(browserSuites).filter(([, suite]) => {
    const seen = new Set();
    const visit = (w) => {
      if (!w || seen.has(w.name)) return false;
      seen.add(w.name);
      return w.location === location || dependencies(w).some((name) => visit(workspaces.find((d) => d.name === name)));
    };
    return visit(workspaces.find((w) => w.location === suite.location));
  }).map(([id]) => id);
  for (const file of files) {
    if (/^docs\/.*\.md$|^[^/]+\.md$/.test(file)) continue;
    if (/^infra\/(?:release\/(?:frontend|listening-artifacts|check-listening-browser)\.mjs|functions\/listening-request\.js\.tftpl|headers\/listening-csp\.txt|cohost-edge\/main\.tf)$/.test(file)) {
      if (plan['offline-player'] !== 'all') plan['offline-player'].push('packages/offline-player/e2e/delivery.spec.ts');
    }
    const suite = Object.entries(browserSuites).find(([, s]) => file.startsWith(`${s.location}/`));
    if (suite && file.startsWith(`${suite[1].location}/${suite[1].specs}/`) && /\.spec\.ts$/.test(file)) {
      if (!exists(file)) all([suite[0]]); // Deleted/renamed source: validate the remaining suite.
      else if (plan[suite[0]] !== 'all') plan[suite[0]].push(file);
      continue;
    }
    if (suite && (file.startsWith(`${suite[1].location}/${suite[1].specs}/`)
      || suite[0] === 'e2e' || /\/(?:[^/]*config\.[^/]+|scripts\/.*|fixtures\/.*)$/.test(file))) {
      all([suite[0]]); continue;
    }
    if (/(^|\/)package(?:-lock)?\.json$/.test(file)) { all(); continue; }
    const owner = workspaces.filter((w) => file.startsWith(`${w.location}/`))
      .sort((a, b) => b.location.length - a.location.length)[0];
    if (owner) {
      const affected = consumers(owner.location);
      const application = /^(frontend-|packages\/(markup|public-presentation|admin-api|seed-loader))/.test(owner.location);
      if (!affected.length && !application) { all(); continue; }
      if (!main) {
        all(affected);
        if (application) all(['e2e']);
      }
      continue;
    }
    if (/^(backend|infra|docker)\//.test(file) || /^docker-compose/.test(file)) {
      if (!main && !/\.(?:test\.mjs|md)$|\/src\/test\//.test(file)) all(['e2e']);
      continue;
    }
    all(); // CI tooling, root dependencies, removed/unknown workspaces: never silently omit checks.
  }
  return Object.fromEntries(Object.entries(plan).map(([id, mode]) => [id,
    mode === 'all' ? mode : [...new Set(mode)].sort()]));
};

export const browserOutputs = (plan) => Object.fromEntries(Object.keys(browserSuites).map((id) =>
  [`browser_${id.replaceAll('-', '_')}`, plan[id] === 'all' || plan[id].length > 0]));
