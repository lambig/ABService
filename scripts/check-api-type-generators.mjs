#!/usr/bin/env node
// 生成物を持つワークスペースが、いまも生成器を持ち、ルートの生成から到達できることを検査する。
//
// 型の同期ゲート（api-types-check）はルートの `generate:api-types` を呼び、ルートの script は
// `--workspaces --if-present` で各ワークスペースの同名 script を回す。この経路は2か所で黙って切れる。
// script が消えれば `--if-present` が飛ばし、ルートの workspaces から外れていれば `--workspaces` が
// そもそも拾わない。どちらの場合もその生成物は作り直されないまま、差分の検査を通る。
//
// 対象は追跡中の生成物の実体から求め、ワークスペースの集合は npm 自身に聞く（ルートの workspaces の
// パターンをここで解釈し直さない）。どちらも名前を写さないので、生成物を持つワークスペースが増えても
// 検査の側は古くならない。

import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const GENERATED = '*/src/lib/api/schema.d.ts'
const GENERATOR = 'generate:api-types'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

const run = (command, args) =>
  execFileSync(command, args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })

const generated = run('git', ['ls-files', GENERATED]).split('\n').filter(Boolean)

if (generated.length === 0) {
  console.error(`No tracked ${GENERATED} was found.`)
  console.error('Either the generated types moved, or they are no longer committed (DECISIONS 33).')
  process.exit(1)
}

const workspaces = JSON.parse(run('npm', ['query', '.workspace']))

// 生成物を抱えるワークスペースは、位置が最も深く一致するもの（packages/foo と packages が
// 両方あるような入れ子でも、実際に持っている側が選ばれる）
const owner = (file) =>
  workspaces
    .filter((workspace) => file.startsWith(`${workspace.location}/`))
    .sort((a, b) => b.location.length - a.location.length)[0]

let failed = false

for (const file of generated) {
  const workspace = owner(file)

  if (!workspace) {
    console.error(`${file} is tracked, but no npm workspace contains it.`)
    console.error('Add its package to "workspaces" in the root package.json; --workspaces skips it otherwise.')
    failed = true
    continue
  }

  if (!workspace.scripts?.[GENERATOR]) {
    console.error(`${file} is committed as a generated file, but ${workspace.location}/package.json has no ${GENERATOR} script.`)
    console.error('Without it --if-present skips the workspace and the stale file passes the diff check.')
    failed = true
    continue
  }

  console.log(`${workspace.name} (${workspace.location}) generates ${file}`)
}

process.exit(failed ? 1 : 0)
