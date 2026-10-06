# Agents: how they're set up and how to use them

This repo is built by Claude Code **subagents** working in parallel. Each agent owns one module, works in its own git worktree, and builds against the shared contract in [`api-contract.md`](api-contract.md).

```
                    main Claude Code session (orchestrator)
                    │   owns: docs/, .claude/, merges, final review
     ┌──────────────┼──────────────┬─────────────────────┐
     ▼              ▼              ▼                     ▼ (after merge)
rag-service-dev  backend-dev   frontend-dev        integration-tester
rag-service/     backend/      frontend/           scripts/, root docs
worktree A       worktree B    worktree C          main checkout
```

## What exists

| File | Purpose |
|---|---|
| `.claude/agents/rag-service-dev.md` | Python FastAPI service, `prompts.py`, refactor of `pdf_rag.py` |
| `.claude/agents/backend-dev.md` | Spring Boot REST API, H2 transcript log, rag-service client |
| `.claude/agents/frontend-dev.md` | React/Vite chat page |
| `.claude/agents/integration-tester.md` | Runs every suite, starts the stack, smoke tests, root docs |
| `.claude/agents/docs-curator.md` | Builds the onboarding knowledge base (`docs/knowledge-base/`) and `docs/CHANGELOG.md` from git history and code |
| `.claude/settings.json` | Commands agents may run without a permission prompt, plus a deny list |
| `docs/api-contract.md` | The HTTP contracts every module builds against |

## Anatomy of an agent file

```markdown
---
name: backend-dev                    # how you refer to it ("use the backend-dev agent")
description: Builds the Spring ...   # Claude reads this to decide when to delegate to it
tools: Read, Write, Edit, Bash, Glob, Grep   # allowlist; omit to inherit every tool
model: inherit                       # or sonnet / opus / haiku
---

System prompt for the agent: what it owns, what it must not touch,
environment facts, tasks, and the "done" criteria.
```

Every agent body in this repo follows the same five sections:
1. **Ownership**: the one folder it may edit. This is what keeps parallel branches from conflicting.
2. **Environment**: facts it can't discover on its own, such as the absolute `.venv` path (the venv isn't inside a worktree) or "don't pip install".
3. **Tasks**: concrete files and behaviors.
4. **Tests**: must run without Ollama, the network or the other modules.
5. **Done criteria**: tests pass, README written, commit on its branch, never push, structured final report.

## How to create a new agent

Either:
- Ask Claude to write it, e.g. "create a code-reviewer subagent in .claude/agents that reviews diffs in backend/ and reports issues without editing files", or
- Write `.claude/agents/<name>.md` by hand using the anatomy above.

(The interactive `/agents` wizard used to do this but has been removed from Claude Code.)

Project agents (`.claude/agents/`) are committed and shared with anyone who clones the repo. Personal agents go in `~/.claude/agents/`. Start a new session if a newly written file doesn't get picked up. Reference: https://code.claude.com/docs/en/sub-agents

## Prerequisites (one-time)

| Need | Status / command |
|---|---|
| Claude Code + git | installed |
| Phase 0 committed | worktrees contain only committed files. They may also start from `origin/main` rather than your branch (it happened here), so record the base with `git log -1 --format=%h` and pass it to every agent |
| Shared Python deps | `fastapi` installed into `.venv` in Phase 0; agents are denied `pip install` so they can't change the shared venv |
| Network | npm registry, Maven Central, start.spring.io |
| Ollama running | only for Phase 2 (`ollama serve`; `gemma4` and `nomic-embed-text` pulled) |
| Permissions | `.claude/settings.json` allows npm/npx/mvnw/venv python/uvicorn/local curl/git commit; denies `git push` |

## Prompts to paste into Claude Code

Run these from the repo root on the `feature/chat-rag-app` branch.

**1. Launch the three module agents in parallel**
```
Launch the rag-service-dev, backend-dev and frontend-dev agents in parallel,
each with worktree isolation. Tell each one its expected base commit is
<output of git log -1 --format=%h>, so it can run the check in its
"Before you start" section. Each one should implement its module per
docs/api-contract.md and its agent instructions, make its tests pass,
write its README, and commit on its own branch. When all three finish,
report for each: branch name, files changed, test results, and any
contract questions it raised. Do not merge yet.
```

**2. Review one agent's work**
```
Check that the backend-dev branch is built on the base commit
(git merge-base --is-ancestor <base> <branch>). Then show me the diff
summary of that branch against feature/chat-rag-app, run its test suite
in that worktree and show me the result.
```

**3. Send a follow-up to an agent that already ran** (it keeps its context while its transcript exists; after that the message fails with "No transcript found", so launch a new agent with the full context instead)
```
Send backend-dev this follow-up: <the change you want>. Re-run its tests
and commit.
```

**4. Merge and integrate**
```
Merge the three agent branches into feature/chat-rag-app one at a time.
Before each merge, run git merge-base --is-ancestor <base> <branch> and
stop if it fails (the branch wasn't built on the contract). Stop if there's
any conflict. Then launch the integration-tester agent (no worktree) with
the resulting HEAD as its expected commit, and report its results.
```

**5. Change the contract mid-flight**
```
Update docs/api-contract.md: <change>. Commit it, then tell every agent
affected by the change exactly what changed and ask it to update its code
and tests.
```

**6. Run one agent alone** (e.g., redo the frontend)
```
Use the frontend-dev agent with worktree isolation to <task>.
```

## Human checkpoints

The agents work on their own, but you review at these points:
1. After launch: read each final report. Agents summarize their own results, so check them against the actual test output.
2. Before merging: confirm each branch is built on the base commit, then look at its diff (prompt 2).
3. After integration: open http://localhost:5173 and ask a question yourself.
