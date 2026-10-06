"""
FastAPI wrapper around the PDF RAG pipeline (Contract B in docs/api-contract.md).

The index is built once at startup in a background thread. Until it is ready,
`/ask` returns 503 and `/health` reports status "loading".

Run from this folder: `../.venv/bin/uvicorn app:app --port 8000`
"""

import logging
import threading
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Optional

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel

from pdf_rag import PDFRAGApp, RAGConfig

logger = logging.getLogger("rag-service")


class AskRequest(BaseModel):
    question: str


class AskResponse(BaseModel):
    answer: str


@dataclass
class IndexState:
    status: str = "loading"  # "loading" | "ok" | "error"
    chunks: int = 0
    detail: Optional[str] = None
    rag: Optional[PDFRAGApp] = None


def build_index(state: IndexState, rag: PDFRAGApp) -> None:
    """Build the index and record the outcome on `state`. Runs in a background thread."""
    try:
        rag.build_index()
    except Exception as exc:  # startup must not crash the server; report it instead
        logger.exception("failed to build index")
        state.detail = str(exc) or type(exc).__name__
        state.status = "error"
    else:
        # Set rag and chunks before status, so a handler that sees "ok" also sees the chain.
        state.rag = rag
        state.chunks = rag.chunk_count
        state.status = "ok"


@asynccontextmanager
async def lifespan(app: FastAPI):
    state = IndexState()
    app.state.index = state
    # Don't await the build: uvicorn binds the port only after startup returns,
    # and /health must be reachable (reporting "loading") while the index builds.
    # Daemon, so a shutdown during a long build isn't held up by it.
    thread = threading.Thread(
        target=build_index, args=(state, PDFRAGApp(RAGConfig())), name="index-build", daemon=True
    )
    app.state.index_thread = thread
    thread.start()
    yield


app = FastAPI(title="rag-service", lifespan=lifespan)
# Default state so handlers work even when the lifespan has not run (e.g. TestClient without `with`).
app.state.index = IndexState()


@app.post("/ask", response_model=AskResponse)
def ask(body: AskRequest, request: Request) -> AskResponse:
    # Sync def on purpose: FastAPI runs it in its threadpool, so the blocking
    # chain call does not block the event loop.
    state: IndexState = request.app.state.index
    if state.status == "loading":
        raise HTTPException(status_code=503, detail="index loading")
    if state.status == "error" or state.rag is None:
        raise HTTPException(status_code=503, detail=f"index unavailable: {state.detail}")

    try:
        answer = state.rag.answer(body.question)
    except Exception:
        logger.exception("chain failed")
        raise HTTPException(status_code=500, detail="answer generation failed")
    if not answer.strip():
        # The model can stop without writing an answer; don't report that as success.
        logger.warning("chain returned an empty answer")
        raise HTTPException(status_code=500, detail="answer generation failed")
    return AskResponse(answer=answer)


@app.get("/health")
def health(request: Request) -> dict:
    state: IndexState = request.app.state.index
    body = {"status": state.status, "chunks": state.chunks}
    if state.detail is not None:
        body["detail"] = state.detail
    return body
