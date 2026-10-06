# PDF RAG

> The repo is being restructured into a chat app (React → Spring Boot → Python rag-service). See `docs/api-contract.md` and `docs/agents.md`. Run the script from inside `rag-service/` so the relative PDF path resolves.

`rag-service/pdf_rag.py` is a simple retrieval-augmented generation example that:

- loads a PDF from `rag-service/data/HOA.pdf`
- splits the PDF into chunks
- creates embeddings with Ollama
- stores the chunks in Chroma
- uses `MultiQueryRetriever` to improve retrieval
- answers a hardcoded question with an Ollama chat model

## Requirements

- Python 3
- Ollama installed and running locally
- a PDF file at `rag-service/data/HOA.pdf`

## Python Dependencies

Install the project dependencies:

```bash
pip install -r rag-service/requirements.txt
```

Current Python packages in `rag-service/requirements.txt`:

- `ollama`
- `chromadb`
- `pdfplumber`
- `langchain`
- `langchain-core`
- `langchain-ollama`
- `langchain-community`
- `langchain_text_splitters`
- `unstructured`
- `unstructured[all-docs]`
- `fastembed`
- `sentence-transformers`
- `elevenlabs`
- `langchain-classic`
- `pypdf`
- `requests`
- `fastapi`
- `uvicorn`
- `httpx`

## Ollama Dependencies

This script depends on a local Ollama server and these models:

- Chat model: `gemma4`
- Embedding model: `nomic-embed-text`

Install Ollama first, then make sure the models are available:

```bash
ollama pull gemma4
ollama pull nomic-embed-text
```

The script already pulls `nomic-embed-text` at runtime before creating embeddings. `gemma4` still needs to be available in Ollama when the script runs.

## What the Script Uses by Default

- PDF path: `rag-service/data/HOA.pdf`
- Chat model: `gemma4`
- Embedding model: `nomic-embed-text`
- Chroma collection: `simple-rag`
- Chunk size: `1200`
- Chunk overlap: `300`
- Question:
  `What are the main points that I should refer to first`

## Notes

- Chroma is created from the PDF content during runtime.
- The script is currently configured for a single hardcoded question.
- `rag-service/pdf_rag.py` is structured around `RAGConfig`, `PDFRAGApp`, and `main()` for easier extension.
