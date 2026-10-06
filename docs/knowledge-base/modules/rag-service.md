# Module: rag-service

Python FastAPI service around a LangChain RAG pipeline. It answers questions about `rag-service/data/HOA.pdf` with the local `gemma4` model through Ollama. Only the backend calls it, over Contract B ([api-contract.md](../../api-contract.md#contract-b--backend--rag-service-fastapi-port-8000)). Owner: `rag-service-dev`. Module README: [rag-service/README.md](../../../rag-service/README.md).

## Files

| File | What it holds |
|---|---|
| `app.py` | FastAPI app, `IndexState` dataclass, `build_index(state, rag)`, the lifespan that starts it in a background thread, `POST /ask`, `GET /health`. |
| `pdf_rag.py` | `RAGConfig` (frozen dataclass), `PDFRAGApp` (pipeline steps), `main()` CLI. |
| `prompts.py` | `QUERY_REWRITE_TEMPLATE`, `SYSTEM_GUARDRAIL`, `RAG_ANSWER_TEMPLATE`, builders `query_rewrite_prompt()` and `rag_answer_prompt()`. |
| `requirements.txt` | Direct dependencies pinned with `==`, plus the OpenTelemetry family pinned to one version (see the comment in the file). |
| `data/HOA.pdf` | The source document. |
| `tests/` | `test_app.py` (10), `test_pdf_rag.py` (9), `test_prompts.py` (6). |

## Entry points

| Entry point | How to run | Notes |
|---|---|---|
| HTTP service | `../.venv/bin/uvicorn app:app --port 8000` from `rag-service/` | Serves at once; builds the index in a background thread (`/health` reports `loading`, then `ok`). |
| CLI | `../.venv/bin/python pdf_rag.py` from `rag-service/` | Builds the index, answers the hardcoded question in `RAGConfig.question`, prints the answer. |
| Tests | `../.venv/bin/python -m unittest discover -s tests -v` | No Ollama, network or real PDF needed. |

## Endpoints

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/ask` | Body `{"question": "..."}`. `200` with `{"answer": "..."}`. `503` with `{"detail": "index loading"}` while loading, or `{"detail": "index unavailable: ..."}` if startup failed. `500` with `{"detail": "answer generation failed"}` if the chain raises or returns a blank answer (blank since `2063d33`). `422` for an invalid body (FastAPI default). |
| `GET` | `/health` | `{"status": "loading" \| "ok" \| "error", "chunks": N}`, plus `"detail"` when status is `error`. |

## Configuration

`RAGConfig` (in `pdf_rag.py`) reads these at construction time:

| Variable | Default | Meaning |
|---|---|---|
| `RAG_DOC_PATH` | `./data/HOA.pdf` | PDF to index. Relative to the working directory. |
| `RAG_MODEL` | `gemma4` | Chat model for query rewriting and answers. |
| `RAG_EMBED_MODEL` | `nomic-embed-text` | Embedding model for Chroma. |

Fixed in `RAGConfig`: collection `simple-rag`, chunk size 1200, overlap 300, `reasoning=False` (passed to `ChatOllama`; see [ADR-022](../decisions.md#adr-022-model-reasoning-off-and-a-blank-answer-is-a-500)).

Dependencies are pinned with `==` in `requirements.txt`, including the OpenTelemetry family at 1.45.0 ([ADR-021](../decisions.md#adr-021-pin-rag-service-dependencies-and-the-opentelemetry-family)).

## Main classes and functions

- `lifespan(app)` in `app.py`: creates `PDFRAGApp(RAGConfig())` and starts the module function `build_index(state, rag)` in a daemon thread stored as `app.state.index_thread`, without waiting for it. That function sets `IndexState` to `ok` (with `chunks`) or `error` (with `detail`).
- `ask(body, request)` in `app.py`: a sync `def` on purpose, so FastAPI runs it in its threadpool. Returns 503 unless the state is `ok`, and 500 if `answer()` raises or returns a blank string.
- `PDFRAGApp` in `pdf_rag.py`:
  - `load_documents()` raises `FileNotFoundError` if the PDF is missing.
  - `split_documents(documents)` uses `RecursiveCharacterTextSplitter`.
  - `build_vector_db(chunks)` calls `ollama.pull()` on the embedding model, then builds an in-memory Chroma store.
  - `build_chain(vector_db)` creates `ChatOllama(model=..., reasoning=...)`, wraps the store in `MultiQueryRetriever` (five rewritten questions) and pipes `{context, question}` into the answer prompt, `ChatOllama` and `StrOutputParser`.
  - `build_index()` runs the four steps above and records `chunk_count`.
  - `answer(question)` calls `chain.invoke`. It raises `RuntimeError` if the index was not built.
  - `run()` is the CLI wrapper: build, answer the configured question, print.

## Tests

25 tests in three files, all using stdlib `unittest`. They mock Ollama and PDF loading. `test_app.py` (10 tests) runs the lifespan through `started_client()`, which enters `TestClient(app)` and joins the index thread, and replaces `PDFRAGApp` with a mock via `patch.object`, so no real index is built.

Verified 2026-10-06 at `be3570f`: `Ran 25 tests ... OK`. `test_startup_does_not_wait_for_index_build` holds the build open and checks `/health` reports `loading` and `/ask` returns 503. `test_empty_answer_returns_500` and `test_build_chain_turns_off_model_reasoning_by_default` cover `2063d33`.

The live path (real Ollama, real PDF) is not in the unit suite. It is checked by `scripts/smoke.sh`; in the Phase 2 run the index reached `ok` with 133 chunks in about 11 seconds.

## Known limitations

- **Index is in memory.** Chroma is never written to disk. Every start re-embeds the PDF. Persisting it is listed as later work.
- **No conversation history.** Each `/ask` is answered on its own ([ADR-003](../decisions.md#adr-003-transcript-only-memory-rag-chain-unchanged)).
- **`ollama.pull()` on every start.** `build_vector_db` pulls the embedding model each time the index is built. This needs Ollama up during startup.
- **Output printed.** `load_documents` and the other steps `print` progress lines, which is noise under uvicorn.
- **Concurrency not tested.** `/ask` runs in a threadpool against one shared chain. Parallel calls were not tested.
- **Model reasoning is off.** `RAGConfig.reasoning=False`. With `gemma4`'s default thinking, answers took 40–84 seconds and sometimes came back empty ([run-book known gaps](../run-book.md#known-gaps), item 14). With it off, answers take 8–22 seconds. There are still two LLM calls per question (query rewrite, then answer).

## Related

- [Architecture](../architecture.md) for where this sits.
- [Decisions](../decisions.md): ADR-001, ADR-012, ADR-014, ADR-015, ADR-019, ADR-020, ADR-021, ADR-022.
- Commit history: `7c26c7d` (original script), `b8b0294` (moved to `rag-service/`), `d065033` (FastAPI and prompts, Phase 1A), `864d09c` (background index build), `be52f79` (pinned requirements), `2063d33` (reasoning off, blank answer is 500).
