import assert from 'node:assert/strict';
import { browserJobs, browserKeys, browserSuites, browserOutputs, browserSelection } from './browser-policy.mjs';

// Workspace tracks own npm workspaces. The infrastructure track owns no workspace:
// its checks read infra/ and the tooling around it, never installed packages.
const workspaceTracks = ['application', 'listening'];
export const tracks = [...workspaceTracks, 'infrastructure'];

// Ownership is deliberately coarse. Unknown workspaces belong to both tracks
// until their boundary is decided; checks must not disappear when packages grow.
const ownership = {
  application: ['frontend-public', 'frontend-admin', 'e2e', 'packages/markup', 'packages/patterns',
    'packages/public-presentation', 'packages/admin-api', 'packages/seed-loader'],
  listening: [
    'packages/audio-dsp', 'packages/audio-worklet', 'packages/installation', 'packages/distribution-client',
    'packages/listening-preparation', 'packages/listening-presentation',
    'packages/player', 'packages/offline-storage', 'packages/offline-shell',
    'packages/offline-player', 'packages/visualizer',
  ],
};

export const jobs = {
  'backend-check': 'application',
  'frontend-check': 'application',
  'api-types-check': 'application',
  'container-check': 'application',
  'container-check-amd64': 'application',
  'iac-check': 'infrastructure',
  e2e: 'application',
  'listening-check': 'listening',
  ...browserJobs,
};

const dependencies = (workspace) => Object.keys({
  ...workspace.dependencies, ...workspace.devDependencies,
  ...workspace.peerDependencies, ...workspace.optionalDependencies,
});

export const trackWorkspaces = (workspaces, track) => {
  assert.ok(workspaceTracks.includes(track), `Unknown CI track: ${track}`);
  const known = Object.values(ownership).flat();
  const roots = workspaces.filter((workspace) =>
    ownership[track].includes(workspace.location) || !known.includes(workspace.location));
  const expand = (selected) => {
    const names = new Set(selected.flatMap(dependencies));
    const next = workspaces.filter((workspace) => selected.includes(workspace) || names.has(workspace.name));
    return next.length === selected.length ? next : expand(next);
  };
  return expand(roots);
};

export const selectTracks = (files, workspaces) => {
  const members = Object.fromEntries(workspaceTracks.map((track) =>
    [track, new Set(trackWorkspaces(workspaces, track).map((workspace) => workspace.name))]));
  const fileTracks = (file) => {
    // Package metadata can change dependency edges or workspace membership.
    // Include deleted paths too: the caller uses --no-renames and no diff filter.
    const owner = workspaces.filter((workspace) => file.startsWith(`${workspace.location}/`))
      .sort((a, b) => b.location.length - a.location.length)[0];
    // Only non-executable documentation is excluded. Unknown code, tooling,
    // workflows and runtime settings require every track. Package metadata
    // concerns workspaces only; infrastructure checks install no packages.
    return [
      { matches: /(^|\/)package(?:-lock)?\.json$/.test(file), selected: workspaceTracks },
      { matches: Boolean(owner), selected: workspaceTracks.filter((track) => members[track].has(owner?.name)) },
      // Application jobs also run infra/ host and release checks, so infra/ keeps both.
      { matches: /^infra\//.test(file), selected: ['application', 'infrastructure'] },
      { matches: /^(backend|frontend-public|frontend-admin|e2e|docker)\//.test(file)
        || /^docker-compose[^/]*\.ya?ml$/.test(file), selected: ['application'] },
      { matches: /^docs\/.*\.md$/.test(file) || /^[^/]+\.md$/.test(file), selected: [] },
      { matches: true, selected: tracks },
    ].find((rule) => rule.matches).selected;
  };
  const selected = new Set(files.length === 0 ? tracks : files.flatMap(fileTracks));
  return Object.fromEntries(tracks.map((track) => [track, selected.has(track)]));
};

export const selectCI = ({ event, ref, files, workspaces, exists }) => {
  const plan = browserSelection({ event, ref, files, workspaces, exists });
  const selected = event === 'pull_request' || (event === 'push' && ref === 'refs/heads/main')
    ? selectTracks(files, workspaces) : Object.fromEntries(tracks.map((track) => [track, true]));
  const browsers = browserOutputs(plan);
  // Application E2E is a step within the application job, not a separate job.
  // A conservative browser fallback must never be hidden by track selection.
  selected.application ||= browsers.browser_e2e;
  return { ...selected, ...browsers, browser_plan: JSON.stringify(plan) };
};

export const frontendTasks = (workspaces, track) => trackWorkspaces(workspaces, track).flatMap((workspace) => {
  const scripts = ['lint', 'typecheck', ...(workspace.location === 'e2e' ? [] : ['test']),
    ...(workspace.location.startsWith('packages/') ? ['build'] : [])]
    .filter((script) => workspace.scripts?.[script]);
  assert.ok(scripts.length > 0, `No frontend checks defined for ${workspace.name}`);
  return scripts.map((script) => ({ workspace: workspace.name, script }));
});

export const gateFailures = (needs, fullChecks = false) => {
  const outputs = needs.changes?.outputs;
  let plan;
  try {
    plan = JSON.parse(outputs?.browser_plan);
    assert.ok(Object.keys(browserSuites).every((id) => plan[id] === 'all' || Array.isArray(plan[id])));
    assert.ok(Object.entries(browserOutputs(plan)).every(([key, selected]) => outputs[key] === String(selected)));
  } catch { return ['Browser selection is missing, invalid or inconsistent']; }
  const validSelection = needs.changes?.result === 'success'
    && [...tracks, ...browserKeys].every((track) => ['true', 'false'].includes(outputs?.[track]));
  if (fullChecks && Object.keys(browserSuites).some((id) => plan[id] !== 'all')) return ['Release/manual CI requires all browser suites'];
  if (fullChecks && tracks.some((track) => outputs[track] !== 'true')) return ['Release/manual CI requires all tracks'];
  return validSelection ? Object.entries(jobs).flatMap(([job, track]) => {
    const result = needs[job]?.result;
    const selected = outputs[track] === 'true';
    const valid = selected ? result === 'success' : ['success', 'skipped'].includes(result);
    return valid ? [] : [`${job}: ${result ?? 'missing'} (required=${selected})`];
  }) : ['CI scope selection did not succeed with valid outputs'];
};
