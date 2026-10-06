import sys
import threading
import unittest
from contextlib import contextmanager
from pathlib import Path
from unittest.mock import MagicMock, patch

RAG_SERVICE_DIR = Path(__file__).resolve().parents[1]
if str(RAG_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(RAG_SERVICE_DIR))

from fastapi.testclient import TestClient  # noqa: E402

import app as app_module  # noqa: E402
from app import IndexState, app  # noqa: E402


def make_fake_rag(chunks=42, answer="The main points are ..."):
    """A stand-in for PDFRAGApp: no Ollama, no PDF."""
    rag = MagicMock()

    def build_index():
        rag.chunk_count = chunks

    rag.build_index.side_effect = build_index
    rag.answer.return_value = answer
    return rag


@contextmanager
def started_client(**kwargs):
    """A TestClient with the lifespan run and the background index build finished."""
    with TestClient(app, **kwargs) as client:
        app.state.index_thread.join(timeout=5)
        yield client


class AppTests(unittest.TestCase):
    def test_ask_returns_answer_after_startup(self):
        rag = make_fake_rag(answer="The main points are ...")

        with patch.object(app_module, "PDFRAGApp", return_value=rag):
            with started_client() as client:
                response = client.post("/ask", json={"question": "What are the main points"})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"answer": "The main points are ..."})
        rag.build_index.assert_called_once_with()
        rag.answer.assert_called_once_with("What are the main points")

    def test_health_reports_ok_and_chunk_count_after_startup(self):
        rag = make_fake_rag(chunks=42)

        with patch.object(app_module, "PDFRAGApp", return_value=rag):
            with started_client() as client:
                response = client.get("/health")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "chunks": 42})

    def test_ask_returns_503_while_index_loading(self):
        rag = make_fake_rag()
        # No lifespan here, so the index stays in the "loading" state.
        client = TestClient(app)
        app.state.index = IndexState(status="loading")
        try:
            with patch.object(app_module, "PDFRAGApp", return_value=rag):
                response = client.post("/ask", json={"question": "anything"})
        finally:
            app.state.index = IndexState()

        self.assertEqual(response.status_code, 503)
        self.assertEqual(response.json(), {"detail": "index loading"})
        rag.answer.assert_not_called()

    def test_health_reports_loading_before_index_ready(self):
        client = TestClient(app)
        app.state.index = IndexState(status="loading")
        try:
            response = client.get("/health")
        finally:
            app.state.index = IndexState()

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "loading", "chunks": 0})

    def test_startup_does_not_wait_for_index_build(self):
        rag = make_fake_rag(chunks=7)
        release = threading.Event()
        finish_build = rag.build_index.side_effect

        def slow_build_index():
            release.wait(timeout=5)
            finish_build()

        rag.build_index.side_effect = slow_build_index

        with patch.object(app_module, "PDFRAGApp", return_value=rag):
            # Entering the client runs the lifespan; it must return while the build is still blocked.
            with TestClient(app) as client:
                try:
                    loading = client.get("/health")
                    ask = client.post("/ask", json={"question": "anything"})
                finally:
                    release.set()
                app.state.index_thread.join(timeout=5)
                ready = client.get("/health")

        self.assertEqual(loading.json(), {"status": "loading", "chunks": 0})
        self.assertEqual(ask.status_code, 503)
        self.assertEqual(ask.json(), {"detail": "index loading"})
        self.assertEqual(ready.json(), {"status": "ok", "chunks": 7})

    def test_startup_failure_reports_error_and_ask_returns_503(self):
        rag = make_fake_rag()
        rag.build_index.side_effect = FileNotFoundError("PDF not found: ./data/HOA.pdf")

        with patch.object(app_module, "PDFRAGApp", return_value=rag), patch.object(app_module, "logger"):
            with started_client() as client:
                health = client.get("/health")
                ask = client.post("/ask", json={"question": "anything"})

        self.assertEqual(health.status_code, 200)
        self.assertEqual(health.json()["status"], "error")
        self.assertEqual(health.json()["chunks"], 0)
        self.assertIn("PDF not found", health.json()["detail"])
        self.assertEqual(ask.status_code, 503)
        self.assertIn("index unavailable", ask.json()["detail"])

    def test_chain_failure_returns_500_without_leaking_error(self):
        rag = make_fake_rag()
        rag.answer.side_effect = RuntimeError("connection refused to secret host")

        with patch.object(app_module, "PDFRAGApp", return_value=rag), patch.object(app_module, "logger"):
            with started_client(raise_server_exceptions=False) as client:
                response = client.post("/ask", json={"question": "anything"})

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json(), {"detail": "answer generation failed"})

    def test_empty_answer_returns_500(self):
        rag = make_fake_rag(answer="  \n")

        with patch.object(app_module, "PDFRAGApp", return_value=rag), patch.object(app_module, "logger"):
            with started_client() as client:
                response = client.post("/ask", json={"question": "anything"})

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json(), {"detail": "answer generation failed"})

    def test_ask_rejects_missing_question_with_422(self):
        rag = make_fake_rag()

        with patch.object(app_module, "PDFRAGApp", return_value=rag):
            with started_client() as client:
                response = client.post("/ask", json={})

        self.assertEqual(response.status_code, 422)
        rag.answer.assert_not_called()

    def test_ask_rejects_non_string_question_with_422(self):
        rag = make_fake_rag()

        with patch.object(app_module, "PDFRAGApp", return_value=rag):
            with started_client() as client:
                response = client.post("/ask", json={"question": 123})

        self.assertEqual(response.status_code, 422)
        rag.answer.assert_not_called()


if __name__ == "__main__":
    unittest.main()
