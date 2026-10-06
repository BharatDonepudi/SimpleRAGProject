# Agent creation: study notes

Notes on how and why the agents in this repo are set up, written for interview review. Each section ends with an **Interview angle**: the question it answers and a short answer.

> Product details (frontmatter fields, commands, flags) reflect Claude Code as of October 2026. Check the current docs before quoting them as exact.

---

## 1. What a subagent is

A subagent is a separate Claude instance that the main session (the **orchestrator**) launches for a task.
- **Own context window.** It starts cold. It doesn't see the main conversation, only the prompt it is given plus its own system prompt (the body of its `.md` file) and any project files it reads, such as `CLAUDE.md`.
- **Own tool allowlist.** The `tools:` field limits what it can do.
- **Returns one message.** The orchestrator only sees the agent's final report, not its intermediate steps. This keeps the orchestrator's context small, but you have to trust or verify the report.
- **Can run in the background and in parallel.** The orchestrator is notified when each one finishes.
- **Can be continued.** A follow-up message (SendMessage) resumes the same agent with its context intact. A new launch starts fresh.

**Interview angle:** *"Why use subagents instead of one long session?"* For context isolation: each agent's window holds only its module, so it doesn't fill up or get confused by unrelated work. They also allow parallelism, specialization through focused system prompts, and least privilege through restricted tools. The cost is that each one starts cold and re-reads context, so you spend more tokens in total.

---

## 2. The agent definition file

Location: `.claude/agents/<name>.md` (project, committed) or `~/.claude/agents/<name>.md` (personal). If the names collide, the project agent wins.

```markdown
---
name: backend-dev
description: Builds the Spring Boot backend ... Use for any work inside backend/.
tools: Read, Write, Edit, Bash, Glob, Grep
model: inherit
---
<system prompt>
```

| Field | What it does | Design choice here |
|---|---|---|
| `name` | Identifier used to invoke it | One per module, named `<module>-dev` |
| `description` | Claude reads it to decide **when to delegate automatically** | Says what it builds and "use for any work inside `<folder>/`", so routing is unambiguous |
| `tools` | Allowlist; omit it to inherit everything | File tools + Bash only. No web, no spawning other agents, so it can't recurse |
| `model` | `inherit` / `sonnet` / `opus` / `haiku` | `inherit`. A cheaper model could handle routine scaffolding, but quality matters more here |

You can create one with `/agents` (an interactive wizard that can draft it for you) or write the file by hand.

**Interview angle:** *"How does Claude know which agent to use?"* From the `description`. Write it like a routing rule: what the agent does and when to use it. Vague descriptions lead to wrong or missed delegation.

---

## 3. Writing the system prompt

The body of the `.md` file is the agent's system prompt. Every agent here uses the same sections:

1. **Ownership**: "edit only under `backend/`". A hard boundary.
2. **Contract**: "read `docs/api-contract.md` and never edit it; if it's wrong, stop and report."
3. **Environment**: facts the agent can't infer. Examples: the venv is at an absolute path outside the worktree; don't `pip install`; generate Spring with this exact Initializr command; don't guess a Spring Boot version.
4. **Tasks**: concrete files, class names, behaviors, edge cases (e.g., "save the user message *before* calling rag-service").
5. **Tests**: they must not need Ollama, the network or other modules.
6. **Done criteria**: tests pass, README written, commit, never push, a structured final report.

Principles behind it:
- **Be explicit about what's out of scope.** Agents take initiative, so you have to state the boundaries.
- **Give it the environment facts.** A cold agent can't know your machine's quirks; otherwise it will guess, and guesses cause failures.
- **Make "done" checkable.** "Tests pass" is a command anyone can rerun; "looks good" isn't.
- **Ask for a structured report.** The orchestrator only sees the final message, so ask for files changed, test output and open questions.
- **Tell it what to do when stuck.** "Stop and report" prevents it from quietly working around a problem.

**Interview angle:** *"What makes a good agent prompt?"* Clear ownership, explicit constraints, environment facts, verifiable done criteria and an escalation path. Write it for a capable contractor who has never seen your repo.

---

## 4. Contract-first parallel development

Agents can only work in parallel if they don't depend on each other's code. `docs/api-contract.md` makes that possible:
- It fixes the JSON shapes, status codes, ports and config keys **before** anyone writes code.
- Each agent mocks its neighbor using the contract's shapes. The frontend mocks `fetch`, the backend mocks rag-service with `MockRestServiceServer`, and rag-service mocks LangChain/Ollama.
- The contract is **read-only to agents**. A change goes through the orchestrator: edit, commit, notify the affected agents.
- Integration (Phase 2) is where you find out whether everyone read the contract the same way.

This is the same idea as consumer-driven contracts or API-first design with human teams. Agents just make the discipline more important, because they can't chat with each other mid-task.

**Interview angle:** *"How do you stop parallel agents from producing parts that don't fit?"* A written, versioned interface contract created before the work starts, mocks built from it, and an integration phase that checks it.

---

## 5. Git worktrees for isolation

A **git worktree** is a second checkout of the same repo, in a different directory and on its own branch, sharing one `.git` database.
- With worktree isolation, each agent gets its own worktree, so three agents can edit files and run builds at the same time without overwriting each other.
- It is created from the **current commit**. That's why Phase 0 committed everything: uncommitted or untracked files (like the original untracked `tests/` and `CLAUDE.md`) would be missing.
- **Gitignored things aren't there either.** `.venv/` isn't in a worktree, which is why the Python agent is given the absolute venv path. `node_modules/` and `target/` are created inside each worktree by that agent's own install or build.
- An unchanged worktree is cleaned up automatically. A changed one leaves a branch for you to review and merge.
- **One folder per agent means merges don't conflict.** Each branch only touches its own top-level directory.

**Interview angle:** *"Why worktrees instead of branches in one folder?"* Branches alone share a single working directory, so two agents can't have two branches checked out at once. Worktrees give each agent a physical directory, which provides real filesystem isolation for parallel edits and builds.

---

## 6. Shared resources: what isolation does NOT cover

Worktrees isolate files. They don't isolate the machine. In this project:

| Shared resource | Risk | Mitigation |
|---|---|---|
| `.venv` (one Python env) | An agent pip-installs and changes the env for everyone | Deps installed up front in Phase 0; `pip install` is on the deny list |
| Ports 8000 / 8080 / 5173 | Two agents start servers on the same port | Phase 1 agents only run unit tests; only the integration tester starts servers |
| Ollama (local, one GPU/CPU) | Concurrent LLM calls are slow or time out | Unit tests mock Ollama; only Phase 2 uses it |
| H2 file `backend/data/chatdb` | Locked file, or test data mixed with real data | Tests use in-memory H2; the file DB is gitignored |
| Maven `~/.m2` and npm cache | Concurrent downloads | Generally safe; both tools lock their caches |

**Interview angle:** *"What can go wrong running agents in parallel?"* Contention on shared state that the filesystem isolation doesn't cover: environments, ports, databases, local services, rate limits. Find each shared resource and either set it up in advance, make it read-only, or schedule it so only one agent uses it.

---

## 7. Permissions and least privilege

Background agents can't easily stop and wait for you to approve a command, so `.claude/settings.json` decides in advance:
- **Allow:** `npm`, `npx`, `./mvnw`, the venv's python/uvicorn, curl to start.spring.io and localhost, `git add`/`commit`/`status`/`diff`/`log`.
- **Deny:** `git push` (nothing leaves the machine without you) and venv `pip install` (protects the shared env).
- Anything not listed still asks for permission, which is the safe default.
- The file is committed, so the policy is shared and code-reviewed. Personal overrides go in `.claude/settings.local.json` (gitignored).
- The `tools:` field in each agent file is the second layer: the agent doesn't have web or agent-spawning tools at all.

**Interview angle:** *"How do you keep autonomous agents safe?"* Least privilege in layers: per-agent tool allowlists, a command allow/deny policy, ownership boundaries in the prompt, no outward actions (push, deploy) without a human, and reviewing the diff before merging.

---

## 8. The orchestration pattern

This is **orchestrator–worker** with phases:

```
Phase 0  (orchestrator, sequential)   contract + restructure + agent files → commit
Phase 1  (3 workers, parallel)        rag-service | backend | frontend, each in a worktree
  ↓ human review of each diff
Phase 2  (1 worker, sequential)       merge → all suites → full stack → smoke test
  ↓ human tries the app
```

- **Sequential where things depend on each other** (the contract before code, a merge before integration). **Parallel where they don't.**
- **Human-in-the-loop checkpoints** come at phase boundaries, not after every step.
- Other patterns to know:
  - **pipeline**: agent A's output feeds agent B
  - **evaluator/critic**: one agent reviews another's work
  - **fan-out/fan-in research**: many agents search, one synthesizes
  - **fork**: a copy of the current session with full context, versus a fresh agent that starts cold

**Interview angle:** *"How do you decide what to parallelize?"* Draw the dependency graph. Anything connected only through a stable interface can run in parallel. Shared foundations go first, integration goes last, and a human reviews where mistakes are expensive.

---

## 9. Verifying agent output

- Agents report their own success, and a report isn't proof. Rerun the tests yourself or have the orchestrator do it in the worktree.
- Watch for: tests that mock so much they test nothing, tests skipped or deleted to get to green, invented library versions or APIs, edits outside the owned folder, quiet changes to the contract.
- Read the diff (`git diff feature/chat-rag-app..<agent-branch> --stat`, then the full diff) before merging.
- The integration phase exists because each module's unit tests can pass while the system as a whole fails.

**Interview angle:** *"Do you trust what the agent says it did?"* No. Verify with objective checks (tests, build, diff review, an end-to-end run). Design "done" so it can be checked mechanically.

---

## 10. Cost and trade-offs

- **Token cost** grows with the number of agents. Each one starts cold and re-reads `CLAUDE.md`, the contract and the code. Three parallel agents use more total tokens than one sequential session, in exchange for less wall-clock time and cleaner context.
- **Coordination cost:** the contract, ownership rules and merges are overhead that a single developer working alone wouldn't need.
- **When NOT to use agents:** small or tightly coupled changes, exploratory work where the design is still changing, or anything where the interface isn't stable yet.
- **When they're worth it:** independent modules behind a stable interface, repetitive well-specified work, or long tasks that would overflow one context window.

**Interview angle:** *"When wouldn't you use multi-agent?"* When the work is coupled or the interface isn't settled. The coordination overhead and the risk of parts not fitting together outweigh the speedup, and one focused session is cheaper and more coherent.

---

## 11. Subagents vs separate sessions vs Agent SDK

| | Subagents (chosen) | Separate sessions | Claude Agent SDK |
|---|---|---|---|
| Set up | `.claude/agents/*.md` | Open N terminals | Write an orchestrator in Python/TS |
| Parallelism | Orchestrator launches them | You do, by hand | Your code does |
| Visibility | Final report per agent | Every step | Whatever you log |
| Billing | Claude Code plan | Claude Code plan | API key, per token |
| Best for | Repo-local parallel dev | Learning, close control | CI and repeatable pipelines |

**Interview angle:** *"How would you take this to CI?"* Move the same agent definitions and prompts into an Agent SDK script (or headless Claude Code) triggered by CI. Keep the contract and done criteria the same, and gate merges on the test suites and human review.

---

## 12. Decisions made in this project (and why)

| Decision | Why |
|---|---|
| Python RAG behind an HTTP service, not a subprocess per request | The script re-embeds the PDF on every run; a long-running service builds the index once |
| H2 file DB instead of Excel for the transcript log | Excel can't handle two writes at once; H2 is embedded, uses SQL, and needs no server |
| Full answer + spinner, no streaming | Simplest working version across three layers; streaming can come later |
| Contract file before any code | Lets three agents work in parallel without talking to each other |
| One owned folder per agent | Merges without conflicts |
| Commit Phase 0 before launching | Worktrees only see committed files |
| Absolute venv path in the Python agent | `.venv` is gitignored, so it isn't in worktrees |
| Deny `pip install` and `git push` | Protect the shared environment; no outward actions without a human |
| Unit tests mock all neighbors and Ollama | Agents test independently and quickly; real integration happens once, in Phase 2 |
| Separate integration-tester agent that can't edit modules | It finds and reports defects to the owner instead of quietly patching them |
