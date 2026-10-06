# Module: rag-service

Python FastAPI service around a LangChain RAG pipeline. It answers questions about `rag-service/data/HOA.pdf` with the local `gemma4` model through Ollama. Only the backend calls it, over Contract B ([api-contract.md](../../api-contract.md#contract-b--backend--rag-service-fastapi-port-8000)). Owner: `rag-service-dev`. Module README: [rag-service/README.md](../../../rag-service/README.md).

## Files

| File | What it holds |
|---|---|
| `app.py` | FastAPI app, `IndexState` dataclass, lifespan that builds the index, `POST /ask`, `GET /health`. |
| `pdf_rag.py` | `RAGConfig` (frozen dataclass), `PDFRAGApp` (pipeline steps), `main()` CLI. |
| `prompts.py` | `QUERY_REWRITE_TEMPLATE`, `SYSTEM_GUARDRAIL`, `RAG_ANSWER_TEMPLATE`, builders `query_rewrite_prompt()` and `rag_answer_prompt()`. |
| `requirements.txt` | Python dependencies (see [known gaps](../run-book.md#known-gaps), item 3). |
| `data/HOA.pdf` | The source document. |
| `tests/` | `test_app.py` (8), `test_pdf_rag.py` (8), `test_prompts.py` (6). |

## Entry points

| Entry point | How to run | Notes |
|---|---|---|
| HTTP service | `../.venv/bin/uvicorn app:app --port 8000` from `rag-service/` | Builds the index in the lifespan, then serves. |
| CLI | `../.venv/bin/python pdf_rag.py` from `rag-service/` | Builds the index, answers the hardcoded question in `RAGConfig.question`, prints the answer. |
| Tests | `../.venv/bin/python -m unittest discover -s tests -v` | No Ollama, network or real PDF needed. |

## Endpoints

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/ask` | Body `{"question": "..."}`. `200` with `{"answer": "..."}`. `503` with `{"detail": "index loading"}` while loading, or `{"detail": "index unavailable: ..."}` if startup failed. `500` with `{"detail": "answer generation failed"}` if the chain raises. `422` for an invalid body (FastAPI default). |
| `GET` | `/health` | `{"status": "loading" \| "ok" \| "error", "chunks": N}`, plus `"detail"` when status is `error`. |

## Configuration

`RAGConfig` (in `pdf_rag.py`) reads these at construction time:

| Variable | Default | Meaning |
|---|---|---|
| `RAG_DOC_PATH` | `./data/HOA.pdf` | PDF to index. Relative to the working directory. |
| `RAG_MODEL` | `gemma4` | Chat model for query rewriting and answers. |
| `RAG_EMBED_MODEL` | `nomic-embed-text` | Embedding model for Chroma. |

Fixed in `RAGConfig`: collection `simple-rag`, chunk size 1200, overlap 300.

## Main classes and functions

- `lifespan(app)` in `app.py`: creates `PDFRAGApp(RAGConfig())`, calls `build_index()` in a threadpool, and sets `IndexState` to `ok` (with `chunks`) or `error` (with `detail`).
- `ask(body, request)` in `app.py`: a sync `def` on purpose, so FastAPI runs it in its threadpool.
- `PDFRAGApp` in `pdf_rag.py`:
  - `load_documents()` raises `FileNotFoundError` if the PDF is missing.
  - `split_documents(documents)` uses `RecursiveCharacterTextSplitter`.
  - `build_vector_db(chunks)` calls `ollama.pull()` on the embedding model, then builds an in-memory Chroma store.
  - `build_chain(vector_db)` wraps the store in `MultiQueryRetriever` (five rewritten questions) and pipes `{context, question}` into the answer prompt, `ChatOllama` and `StrOutputParser`.
  - `build_index()` runs the four steps above and records `chunk_count`.
  - `answer(question)` calls `chain.invoke`. It raises `RuntimeError` if the index was not built.
  - `run()` is the CLI wrapper: build, answer the configured question, print.

## Tests

22 tests in three files, all using stdlib `unittest`. They mock Ollama and PDF loading. `test_app.py` (8 tests) runs the lifespan through `with TestClient(app)` and replaces `PDFRAGApp` with a mock via `patch.object`, so no real index is built.

Verified 2026-10-06: `22 tests OK`.

## Known limitations

- **Startup blocks the port.** The index is built inside the lifespan. uvicorn runs lifespan startup before it binds the socket, so `/health` cannot report `"loading"` during the build. The `app.py` docstring and the README say otherwise. Details: [run-book known gaps](../run-book.md#known-gaps), item 1 and 2.
- **Index is in memory.** Chroma is never written to disk. Every start re-embeds the PDF. Persisting it is listed as later work.
- **No conversation history.** Each `/ask` is answered on its own ([ADR-003](../decisions.md#adr-003-transcript-only-memory-rag-chain-unchanged)).
- **`ollama.pull()` on every start.** `build_vector_db` pulls the embedding model each time the index is built. This needs Ollama up during startup.
- **Output printed.** `load_documents` and the other steps `print` progress lines, which is noise under uvicorn.
- **Dependencies.** Duplicate `pdfplumber` line and several packages not imported in code ([run-book known gaps](../run-book.md#known-gaps), item 3).
- **Concurrency not tested.** `/ask` runs in a threadpool against one shared chain. Parallel calls were not tested.
- **Real chain not run.** On 2026-10-06 the service started and read the PDF (`done loading...`, `done splitting...`), but the index build failed at the chromadb import, so `/ask` could not be exercised. Ollama itself was running with both models. The 503 and 500 paths come from reading the code. See [run-book](../run-book.md#common-failures).

## Related

- [Architecture](../architecture.md) for where this sits.
- [Decisions](../decisions.md): ADR-001, ADR-012, ADR-014, ADR-015, ADR-019.
- Commit history: `7c26c7d` (original script), `b8b0294` (moved to `rag-service/`), `d065033` (FastAPI and prompts, Phase 1A).
