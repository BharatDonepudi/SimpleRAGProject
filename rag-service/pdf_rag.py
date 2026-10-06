"""
Simple PDF RAG pipeline.

Run `pip install -r requirements.txt` to install dependencies.
Run `python pdf_rag.py` for the one-shot CLI, or start the HTTP service with
`../.venv/bin/uvicorn app:app --port 8000` from this folder.
"""

import os
from dataclasses import dataclass, field
from pathlib import Path

import ollama
from langchain_classic.retrievers import MultiQueryRetriever
from langchain_community.document_loaders import PyPDFLoader
from langchain_community.vectorstores import Chroma
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnablePassthrough
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from prompts import query_rewrite_prompt, rag_answer_prompt


def _env(name: str, default: str) -> str:
    """Read an env var at config construction time, falling back to the default."""
    return os.environ.get(name, default)


@dataclass(frozen=True)
class RAGConfig:
    # Defaults can be overridden with RAG_DOC_PATH, RAG_MODEL and RAG_EMBED_MODEL.
    doc_path: str = field(default_factory=lambda: _env("RAG_DOC_PATH", "./data/HOA.pdf"))
    model: str = field(default_factory=lambda: _env("RAG_MODEL", "gemma4"))
    embedding_model: str = field(
        default_factory=lambda: _env("RAG_EMBED_MODEL", "nomic-embed-text")
    )
    collection_name: str = "simple-rag"
    chunk_size: int = 1200
    chunk_overlap: int = 300
    # gemma4 "thinks" by default. The reasoning is discarded, makes answers about 4x
    # slower, and can use up the token budget so the answer comes back empty.
    reasoning: bool = False
    question: str = "What are the main points that I should refer to first"


class PDFRAGApp:
    def __init__(self, config: RAGConfig) -> None:
        self.config = config
        self.pdf_file = Path(config.doc_path)
        self.chain = None
        self.chunk_count = 0

    def load_documents(self):
        if not self.pdf_file.exists():
            raise FileNotFoundError(f"PDF not found: {self.pdf_file}")

        loader = PyPDFLoader(str(self.pdf_file))
        documents = loader.load()
        print("done loading...")
        return documents

    def split_documents(self, documents):
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=self.config.chunk_size,
            chunk_overlap=self.config.chunk_overlap,
        )
        chunks = splitter.split_documents(documents)
        print("done splitting...")
        return chunks

    def build_vector_db(self, chunks):
        ollama.pull(self.config.embedding_model)

        vector_db = Chroma.from_documents(
            documents=chunks,
            embedding=OllamaEmbeddings(model=self.config.embedding_model),
            collection_name=self.config.collection_name,
        )
        print("done adding vector DB ...")
        return vector_db

    def build_chain(self, vector_db):
        llm = ChatOllama(model=self.config.model, reasoning=self.config.reasoning)
        retriever = MultiQueryRetriever.from_llm(
            vector_db.as_retriever(),
            llm,
            prompt=query_rewrite_prompt(),
        )

        return (
            {"context": retriever, "question": RunnablePassthrough()}
            | rag_answer_prompt()
            | llm
            | StrOutputParser()
        )

    def build_index(self) -> None:
        """Load the PDF, split it, build the vector DB and the chain.

        Results are stored on the instance (`self.chain`, `self.chunk_count`).
        """
        documents = self.load_documents()
        chunks = self.split_documents(documents)
        self.chunk_count = len(chunks)
        vector_db = self.build_vector_db(chunks)
        self.chain = self.build_chain(vector_db)

    def answer(self, question: str) -> str:
        """Answer a question with the index built by `build_index()`."""
        if self.chain is None:
            raise RuntimeError("index not built; call build_index() first")
        return self.chain.invoke(input=question)

    def run(self) -> str:
        """CLI wrapper: build the index, answer the configured question, print it."""
        self.build_index()
        response = self.answer(self.config.question)
        print(response)
        return response


def main() -> str:
    app = PDFRAGApp(RAGConfig())
    return app.run()


if __name__ == "__main__":
    main()
