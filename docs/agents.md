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
- Run `/agents` in Claude Code and pick "Create new agent" (it can draft one for you), or
- Write `.claude/agents/<name>.md` by hand using the anatomy above.

Project agents (`.claude/agents/`) are committed and shared with anyone who clones the repo. Personal agents go in `~/.claude/agents/`. Restart the session, or re-open `/agents`, if a newly written file doesn't show up.

## Prerequisites (one-time)

| Need | Status / command |
|---|---|
| Claude Code + git | installed |
| Phase 0 committed | worktrees branch from the current commit, so uncommitted files don't exist inside them |
| Shared Python deps | `fastapi` installed into `.venv` in Phase 0; agents are denied `pip install` so they can't change the shared venv |
| Network | npm registry, Maven Central, start.spring.io |
| Ollama running | only for Phase 2 (`ollama serve`; `gemma4` and `nomic-embed-text` pulled) |
| Permissions | `.claude/settings.json` allows npm/npx/mvnw/venv python/uvicorn/local curl/git commit; denies `git push` |

## Prompts to paste into Claude Code

Run these from the repo root on the `feature/chat-rag-app` branch.

**1. Launch the three module agents in parallel**
```
Launch the rag-service-dev, backend-dev and frontend-dev agents in parallel,
each with worktree isolation. Each one should implement its module per
docs/api-contract.md and its agent instructions, make its tests pass,
write its README, and commit on its own branch. When all three finish,
report for each: branch name, files changed, test results, and any
contract questions it raised. Do not merge yet.
```

**2. Review one agent's work**
```
Show me the diff summary of the backend-dev branch against
feature/chat-rag-app, then run its test suite in that worktree and show
me the result.
```

**3. Send a follow-up to an agent that already ran** (it keeps its context)
```
Send backend-dev this follow-up: <the change you want>. Re-run its tests
and commit.
```

**4. Merge and integrate**
```
Merge the three agent branches into feature/chat-rag-app one at a time
and stop if there's any conflict. Then launch the integration-tester
agent (no worktree) and report its results.
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
2. Before merging: look at each diff (prompt 2).
3. After integration: open http://localhost:5173 and ask a question yourself.
