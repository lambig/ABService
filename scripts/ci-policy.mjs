import assert from 'node:assert/strict';

export const tracks = ['application', 'listening'];

// Ownership is deliberately coarse. Unknown workspaces belong to both tracks
// until their boundary is decided; checks must not disappear when packages grow.
const ownership = {
  application: ['frontend-public', 'frontend-admin', 'e2e', 'packages/markup', 'packages/patterns'],
  listening: [
    'packages/audio-dsp', 'packages/audio-worklet', 'packages/installation',
    'packages/player', 'packages/offline-storage', 'packages/offline-shell',
    'packages/offline-player', 'packages/visualizer',
  ],
};

export const jobs = {
  'backend-check': 'application',
  'frontend-check': 'application',
  'api-types-check': 'application',
  'container-check': 'application',
  'iac-check': 'application',
  e2e: 'application',
  'listening-check': 'listening',
  'player-browser': 'listening',
  'storage-browser': 'listening',
  'shell-browser': 'listening',
  'offline-player-browser': 'listening',
};

const dependencies = (workspace) => Object.keys({
  ...workspace.dependencies, ...workspace.devDependencies,
  ...workspace.peerDependencies, ...workspace.optionalDependencies,
});

export const trackWorkspaces = (workspaces, track) => {
  assert.ok(tracks.includes(track), `Unknown CI track: ${track}`);
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
  const members = Object.fromEntries(tracks.map((track) =>
    [track, new Set(trackWorkspaces(workspaces, track).map((workspace) => workspace.name))]));
  const fileTracks = (file) => {
    // Package metadata can change dependency edges or workspace membership.
    // Include deleted paths too: the caller uses --no-renames and no diff filter.
    const owner = workspaces.filter((workspace) => file.startsWith(`${workspace.location}/`))
      .sort((a, b) => b.location.length - a.location.length)[0];
    // Only non-executable documentation is excluded. Unknown code, tooling,
    // workflows, root dependencies and runtime settings require both tracks.
    return [
      { matches: /(^|\/)package(?:-lock)?\.json$/.test(file), selected: tracks },
      { matches: Boolean(owner), selected: tracks.filter((track) => members[track].has(owner?.name)) },
      { matches: /^(backend|frontend-public|frontend-admin|e2e|infra|docker)\//.test(file)
        || /^docker-compose[^/]*\.ya?ml$/.test(file), selected: ['application'] },
      { matches: /^docs\/.*\.md$/.test(file) || /^[^/]+\.md$/.test(file), selected: [] },
      { matches: true, selected: tracks },
    ].find((rule) => rule.matches).selected;
  };
  const selected = new Set(files.length === 0 ? tracks : files.flatMap(fileTracks));
  return Object.fromEntries(tracks.map((track) => [track, selected.has(track)]));
};

export const frontendTasks = (workspaces, track) => trackWorkspaces(workspaces, track).flatMap((workspace) => {
  const scripts = ['lint', 'typecheck', ...(workspace.location === 'e2e' ? [] : ['test']),
    ...(workspace.location.startsWith('packages/') ? ['build'] : [])]
    .filter((script) => workspace.scripts?.[script]);
  assert.ok(scripts.length > 0, `No frontend checks defined for ${workspace.name}`);
  return scripts.map((script) => ({ workspace: workspace.name, script }));
});

export const gateFailures = (needs) => {
  const outputs = needs.changes?.outputs;
  const validSelection = needs.changes?.result === 'success'
    && tracks.every((track) => ['true', 'false'].includes(outputs?.[track]));
  return validSelection ? Object.entries(jobs).flatMap(([job, track]) => {
    const result = needs[job]?.result;
    const selected = outputs[track] === 'true';
    const valid = selected ? result === 'success' : ['success', 'skipped'].includes(result);
    return valid ? [] : [`${job}: ${result ?? 'missing'} (required=${selected})`];
  }) : ['CI scope selection did not succeed with valid outputs'];
};
