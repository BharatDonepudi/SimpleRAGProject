# PDF RAG

`pdf-rag.py` is a simple retrieval-augmented generation example that:

- loads a PDF from `./data/HOA.pdf`
- splits the PDF into chunks
- creates embeddings with Ollama
- stores the chunks in Chroma
- uses `MultiQueryRetriever` to improve retrieval
- answers a hardcoded question with an Ollama chat model

## Requirements

- Python 3
- Ollama installed and running locally
- a PDF file at `./data/HOA.pdf`

## Python Dependencies

Install the project dependencies:

```bash
pip install -r requirements.txt
```

Current Python packages in `requirements.txt`:

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

- PDF path: `./data/HOA.pdf`
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
- `pdf-rag.py` is structured around `RAGConfig`, `PDFRAGApp`, and `main()` for easier extension.
