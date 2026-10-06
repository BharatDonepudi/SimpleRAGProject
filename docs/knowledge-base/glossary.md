# Glossary

Terms as they are used in this repo's code and docs. Terms are listed alphabetically.

**Chain.** The LangChain pipeline built by `PDFRAGApp.build_chain()`: retriever, then answer prompt, then `ChatOllama`, then `StrOutputParser`. Its input is a question string and its output is the answer string. See [rag-service](modules/rag-service.md).

**Chroma.** The vector database used by rag-service. It runs in memory and is rebuilt on every start. Collection name `simple-rag`. See [architecture](architecture.md#what-is-stored-where).

**Chunk.** A piece of the PDF produced by `RecursiveCharacterTextSplitter` (size 1200, overlap 300). Each chunk is embedded and stored in Chroma. `/health` reports the number of chunks as `chunks`.

**Contract.** `docs/api-contract.md`. The single source of truth for every HTTP boundary. Contract A is frontend to backend. Contract B is backend to rag-service. See [contract-history](contract-history.md).

**conversationId.** UUID string that identifies one conversation. Null or omitted in `POST /api/chat` starts a new one. It is returned on success, and on 500, 503 and 504 once the conversation exists. The frontend keeps it in `sessionStorage`. See [ADR-011](decisions.md#adr-011-conversationid-on-503-504-and-500).

**Embedding.** A vector that represents a piece of text. rag-service makes embeddings with `nomic-embed-text` through Ollama (`OllamaEmbeddings`). Similar text gets similar vectors, which is how retrieval works.

**gemma4.** The default chat model (`RAG_MODEL`). Used for query rewriting and for the final answer, through Ollama.

**H2.** The embedded Java database used by the backend. Runs in file mode at `backend/data/chatdb`. See [ADR-002](decisions.md#adr-002-h2-file-database-for-the-transcript-log).

**Index.** The vector store and its chunks, built once at rag-service startup by `build_index()`, in a background thread. Also the state name `ok` in `/health`. Not persisted.

**Index thread.** The daemon thread named `index-build` that the lifespan starts to run `build_index(state, rag)` in `app.py`. Stored as `app.state.index_thread` so tests can join it. See [ADR-020](decisions.md#adr-020-build-the-index-in-a-background-thread).

**IndexState.** The dataclass in `rag-service/app.py` that holds `status` (`loading`, `ok`, `error`), `chunks`, `detail` and the `PDFRAGApp` instance.

**Lifespan.** FastAPI's startup and shutdown hook. In `app.py` it starts the index build in a background thread and returns at once, so uvicorn binds the port while the index builds.

**Message (backend).** A row in the `messages` table: role (`user` or `assistant`), content, creation time. Shown as `{ role, content, createdAt }` in the transcript endpoint. `role` is lowercased in the API.

**MultiQueryRetriever.** A LangChain retriever that asks the LLM to rewrite the question into five variants, runs a search for each, and merges the results. Used in `build_chain()`. The rewrite prompt is `QUERY_REWRITE_TEMPLATE` in `prompts.py`.

**Ollama.** The local model server on port 11434. Serves `gemma4` and `nomic-embed-text`. Not part of this repo.

**PDFRAGApp.** The class in `pdf_rag.py` that runs the pipeline. Its methods are `load_documents`, `split_documents`, `build_vector_db`, `build_chain`, `build_index`, `answer` and `run`.

**Phase.** A numbered step in the [implementation plan](../implementation-plan.md): Phase 0 (foundation), Phase 1A to 1C (the three modules, in parallel), Phase 2 (integration). All three were done on 2026-10-06.

**RagClient.** The backend class that calls `POST /ask` on rag-service and maps its failures to `RagUnavailableException`, `RagTimeoutException` or `RagFailedException`.

**RAGConfig.** A frozen dataclass in `pdf_rag.py` with all tunables: document path, models, collection name, chunk size and overlap, `reasoning`, and the CLI question. The first three read environment variables.

**Reasoning (thinking).** Hidden reasoning tokens that some models, including `gemma4`, generate before the answer. Turned off with `RAGConfig.reasoning=False`, passed to `ChatOllama`. With it on, answers were about 4x slower and sometimes empty (`done_reason=length`, meaning the model hit its token limit). See [ADR-022](decisions.md#adr-022-model-reasoning-off-and-a-blank-answer-is-a-500).

**Retriever.** The part of the chain that finds relevant chunks for a question. Here it is a `MultiQueryRetriever` over the Chroma store.

**Role.** The backend enum `USER` or `ASSISTANT` stored on each message.

**sessionStorage.** Browser storage that lasts for one tab. Holds the conversation id under the key `chat.conversationId`. See [ADR-016](decisions.md#adr-016-conversation-id-in-sessionstorage).

**Smoke test.** `scripts/smoke.sh`. A bash script that checks a running stack end to end: both health endpoints, one real question, the transcript, and the `400` and `404` error shapes. Added in Phase 2 (`be3570f`). See the [run book](run-book.md#start-order).

**started_client().** Test helper in `rag-service/tests/test_app.py`. Enters `TestClient(app)`, which runs the lifespan, then joins the index thread so the test sees a finished build.

**StrOutputParser.** LangChain step that turns the model's message into a plain string.

**Transcript.** The stored list of messages for one conversation, oldest first. Served by `GET /api/conversations/{id}/messages`. It is the only memory in the system; the RAG chain does not read it ([ADR-003](decisions.md#adr-003-transcript-only-memory-rag-chain-unchanged)).

**UNAVAILABLE, TIMEOUT, FAILED.** The three values of `AnswerUnavailableException.Reason`. They map to 503, 504 and 500.

**Vite proxy.** The dev-server rule in `frontend/vite.config.js` that forwards `/api` to `http://localhost:8080`. It is why the browser needs no CORS setup. See [ADR-017](decisions.md#adr-017-vite-dev-proxy-instead-of-cors).

**Worktree.** A git feature that checks out a second copy of the repo on another branch in its own folder. Phase 1 agents each worked in one, under `.claude/worktrees/`. Worktrees do not contain gitignored files such as `.venv/`, so agents use absolute paths. All of them were removed on 2026-10-06. See [ADR-009](decisions.md#adr-009-parallel-subagents-in-git-worktrees).
