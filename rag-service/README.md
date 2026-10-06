# rag-service

FastAPI wrapper around a LangChain RAG pipeline that answers questions about `data/HOA.pdf` using a local LLM served by Ollama. The Spring Boot backend calls it over HTTP; see `Contract B` in `../docs/api-contract.md`.

```
backend (:8080) ──HTTP──▶ rag-service (:8000) ──▶ Ollama (:11434)
```

## Prerequisites

- Python 3.13 (the repo virtualenv was built with it)
- [Ollama](https://ollama.com) installed and running (`ollama serve`, default port 11434)
- The repo virtualenv at `../.venv/` (shared by all Python code, gitignored)

## Setup

Activate the virtualenv (optional if you use the absolute paths shown below):

```bash
source ../.venv/bin/activate
```

Install the dependencies:

```bash
pip install -r requirements.txt
```

Pull the Ollama models (chat model and embedding model):

```bash
ollama pull gemma4
ollama pull nomic-embed-text
```

## Run

Run from this folder, because the default PDF path `./data/HOA.pdf` is relative to it:

```bash
../.venv/bin/uvicorn app:app --port 8000
```

On startup the service loads the PDF, builds the in-memory vector index and then reports ready. This takes a while, so `/ask` returns `503` until it finishes. Check progress with `GET /health`.

Endpoints:

| Method | Path | Notes |
|---|---|---|
| `POST` | `/ask` | Body `{"question": "..."}`. Returns `{"answer": "..."}`. `503` while the index loads, `500` if the chain fails or the model returns a blank answer. |
| `GET` | `/health` | `{"status": "loading" \| "ok" \| "error", "chunks": N}`, plus `"detail"` on error. |

Example:

```bash
curl -s localhost:8000/health
curl -s -X POST localhost:8000/ask -H 'Content-Type: application/json' \
  -d '{"question": "What are the main points that I should refer to first"}'
```

The one-shot CLI (asks the hardcoded question and prints the answer) still works:

```bash
../.venv/bin/python pdf_rag.py
```

## Test

All tests (stdlib `unittest`; no Ollama, network or real PDF needed):

```bash
../.venv/bin/python -m unittest discover -s tests -v
```

A single module or test:

```bash
../.venv/bin/python -m unittest tests.test_app -v
../.venv/bin/python -m unittest tests.test_pdf_rag.PDFRAGTests.test_run_preserves_pipeline_flow
```

## Configuration

Environment variables (defaults in `RAGConfig` in `pdf_rag.py`):

| Variable | Default | Meaning |
|---|---|---|
| `RAG_DOC_PATH` | `./data/HOA.pdf` | PDF to index. Relative paths resolve from the working directory. |
| `RAG_MODEL` | `gemma4` | Ollama chat model used for query rewriting and answers. |
| `RAG_EMBED_MODEL` | `nomic-embed-text` | Ollama embedding model used for the vector store. |

Model reasoning ("thinking") is off: `RAGConfig.reasoning` defaults to `False`. With it on, `gemma4` answers about 4x slower and sometimes returns an empty answer.

Example:

```bash
RAG_MODEL=llama3 ../.venv/bin/uvicorn app:app --port 8000
```

## Layout

- `app.py`: FastAPI app, startup index build, `/ask` and `/health`.
- `pdf_rag.py`: `RAGConfig`, `PDFRAGApp` (`build_index()`, `answer()`, `run()`) and the CLI `main()`.
- `prompts.py`: prompt templates and their builders. Each prompt is a named constant plus a test of its input variables.
- `tests/`: unit tests. They patch Ollama and the PDF loading.
