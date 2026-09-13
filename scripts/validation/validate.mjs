#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { createWriteStream, existsSync } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, '..', '..');
const validationDir = path.join(rootDir, '.validation');
const logDir = path.join(validationDir, 'logs');
const summaryPath = path.join(validationDir, 'summary.json');
const isWindows = process.platform === 'win32';
const npmCommand = isWindows ? 'npm.cmd' : 'npm';
const mavenCommand = isWindows ? 'mvnw.cmd' : './mvnw';
const TAIL_LINE_LIMIT = 40;

function usage() {
  console.log(`Usage: node scripts/validation/validate.mjs <backend|frontend|all>\n\nRuns broad deterministic local validation with concise console output.\nComplete logs are written under .validation/logs/.`);
}

function failUsage(message) {
  console.error(message);
  usage();
  process.exitCode = 2;
}

function commandText(check) {
  return [check.command, ...check.args].join(' ');
}

function appendTail(lines, chunk) {
  const next = chunk.toString().split(/\r?\n/);
  for (const line of next) {
    if (line.length === 0) continue;
    lines.push(line);
    if (lines.length > TAIL_LINE_LIMIT) lines.shift();
  }
}

async function runCheck(check) {
  const cwd = path.join(rootDir, check.cwd);
  const logPath = path.join(logDir, `${check.id}.log`);
  const log = createWriteStream(logPath, { flags: 'w' });
  const tail = [];
  const startedAt = new Date();
  const startNs = process.hrtime.bigint();

  return new Promise((resolve) => {
    let settled = false;
    let child;

    try {
      child = spawn(check.command, check.args, {
        cwd,
        env: process.env,
        shell: isWindows,
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe'],
      });
    } catch (error) {
      log.end(String(error));
      resolve({
        id: check.id,
        status: 'INCONCLUSIVE',
        command: commandText(check),
        cwd: check.cwd,
        exitCode: null,
        startedAt: startedAt.toISOString(),
        durationMs: Number((process.hrtime.bigint() - startNs) / 1000000n),
        log: path.relative(rootDir, logPath),
        detail: String(error),
      });
      return;
    }

    const finish = (result) => {
      if (settled) return;
      settled = true;
      log.end(() => resolve(result));
    };

    child.stdout.on('data', (chunk) => {
      log.write(chunk);
      appendTail(tail, chunk);
    });

    child.stderr.on('data', (chunk) => {
      log.write(chunk);
      appendTail(tail, chunk);
    });

    child.on('error', (error) => {
      finish({
        id: check.id,
        status: 'INCONCLUSIVE',
        command: commandText(check),
        cwd: check.cwd,
        exitCode: null,
        startedAt: startedAt.toISOString(),
        durationMs: Number((process.hrtime.bigint() - startNs) / 1000000n),
        log: path.relative(rootDir, logPath),
        detail: String(error),
        tail,
      });
    });

    child.on('close', (code, signal) => {
      finish({
        id: check.id,
        status: code === 0 ? 'PASS' : 'FAIL',
        command: commandText(check),
        cwd: check.cwd,
        exitCode: code,
        signal,
        startedAt: startedAt.toISOString(),
        durationMs: Number((process.hrtime.bigint() - startNs) / 1000000n),
        log: path.relative(rootDir, logPath),
        ...(code === 0 ? {} : { tail }),
      });
    });
  });
}

function preflight(scope) {
  const issues = [];

  if ((scope === 'backend' || scope === 'all') && !existsSync(path.join(rootDir, 'backend', isWindows ? 'mvnw.cmd' : 'mvnw'))) {
    issues.push(`backend/${isWindows ? 'mvnw.cmd' : 'mvnw'} is missing`);
  }

  if (scope === 'frontend' || scope === 'all') {
    if (!existsSync(path.join(rootDir, 'frontend', 'package.json'))) {
      issues.push('frontend/package.json is missing');
    }
    if (!existsSync(path.join(rootDir, 'frontend', 'node_modules'))) {
      issues.push('frontend/node_modules is missing; install locked dependencies before validation');
    }
  }

  return issues;
}

function checksFor(scope) {
  const checks = [];

  if (scope === 'backend' || scope === 'all') {
    checks.push({
      id: 'backend-clean-verify',
      cwd: 'backend',
      command: mavenCommand,
      args: ['-B', '-ntp', 'clean', 'verify'],
    });
  }

  if (scope === 'frontend' || scope === 'all') {
    checks.push(
      { id: 'frontend-lint', cwd: 'frontend', command: npmCommand, args: ['run', 'lint'] },
      { id: 'frontend-typecheck', cwd: 'frontend', command: npmCommand, args: ['run', 'typecheck'] },
      { id: 'frontend-test', cwd: 'frontend', command: npmCommand, args: ['test'] },
      { id: 'frontend-build', cwd: 'frontend', command: npmCommand, args: ['run', 'build'] },
    );
  }

  checks.push({
    id: 'git-diff-check',
    cwd: '.',
    command: 'git',
    args: ['diff', '--check'],
  });

  return checks;
}

async function main() {
  const [scope, ...extraArgs] = process.argv.slice(2);

  if (scope === '--help' || scope === '-h') {
    usage();
    return;
  }

  if (!['backend', 'frontend', 'all'].includes(scope) || extraArgs.length > 0) {
    failUsage('Expected exactly one scope: backend, frontend, or all.');
    return;
  }

  await mkdir(logDir, { recursive: true });

  const startedAt = new Date();
  const preflightIssues = preflight(scope);
  const summary = {
    schemaVersion: 1,
    scope,
    startedAt: startedAt.toISOString(),
    finishedAt: null,
    verdict: null,
    preflightIssues,
    checks: [],
  };

  console.log(`HIPPOCAMPUS VALIDATION — ${scope}`);

  if (preflightIssues.length > 0) {
    summary.verdict = 'INCONCLUSIVE';
    summary.finishedAt = new Date().toISOString();
    await writeFile(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
    for (const issue of preflightIssues) console.log(`INCONCLUSIVE  ${issue}`);
    console.log(`Summary: ${path.relative(rootDir, summaryPath)}`);
    process.exitCode = 2;
    return;
  }

  for (const check of checksFor(scope)) {
    process.stdout.write(`RUN           ${check.id}\n`);
    const result = await runCheck(check);
    summary.checks.push(result);
    console.log(`${result.status.padEnd(13)} ${check.id}`);

    if (result.status !== 'PASS') {
      if (result.tail?.length) {
        console.log('\nFailure tail:');
        for (const line of result.tail) console.log(line);
      }
      break;
    }
  }

  const failed = summary.checks.find((check) => check.status === 'FAIL');
  const inconclusive = summary.checks.find((check) => check.status === 'INCONCLUSIVE');
  summary.verdict = failed ? 'FAIL' : inconclusive ? 'INCONCLUSIVE' : 'PASS';
  summary.finishedAt = new Date().toISOString();
  await writeFile(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');

  console.log(`\nVERDICT       ${summary.verdict}`);
  console.log(`Summary: ${path.relative(rootDir, summaryPath)}`);
  if (summary.checks.length > 0) {
    console.log(`Logs: ${path.relative(rootDir, logDir)}`);
  }

  if (summary.verdict === 'FAIL') process.exitCode = 1;
  if (summary.verdict === 'INCONCLUSIVE') process.exitCode = 2;
}

await main();
