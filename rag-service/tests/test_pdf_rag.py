import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

RAG_SERVICE_DIR = Path(__file__).resolve().parents[1]
if str(RAG_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(RAG_SERVICE_DIR))

import pdf_rag  # noqa: E402

ENV_KEYS = ("RAG_DOC_PATH", "RAG_MODEL", "RAG_EMBED_MODEL")


class PDFRAGTests(unittest.TestCase):
    def test_run_preserves_pipeline_flow(self):
        config = pdf_rag.RAGConfig(question="test question")
        app = pdf_rag.PDFRAGApp(config)
        fake_documents = [object()]
        fake_chunks = [object(), object()]
        fake_vector_db = object()
        fake_chain = MagicMock()
        fake_chain.invoke.return_value = "final answer"

        with (
            patch.object(app, "load_documents", return_value=fake_documents) as load_documents,
            patch.object(app, "split_documents", return_value=fake_chunks) as split_documents,
            patch.object(app, "build_vector_db", return_value=fake_vector_db) as build_vector_db,
            patch.object(app, "build_chain", return_value=fake_chain) as build_chain,
            patch("builtins.print") as print_mock,
        ):
            response = app.run()

        self.assertEqual(response, "final answer")
        load_documents.assert_called_once_with()
        split_documents.assert_called_once_with(fake_documents)
        build_vector_db.assert_called_once_with(fake_chunks)
        build_chain.assert_called_once_with(fake_vector_db)
        fake_chain.invoke.assert_called_once_with(input="test question")
        print_mock.assert_called_once_with("final answer")

    def test_main_uses_default_config(self):
        with patch.object(pdf_rag.PDFRAGApp, "run", return_value="ok") as run_mock:
            response = pdf_rag.main()

        self.assertEqual(response, "ok")
        run_mock.assert_called_once_with()

    def test_build_index_stores_chain_and_chunk_count(self):
        app = pdf_rag.PDFRAGApp(pdf_rag.RAGConfig())
        fake_chunks = [object(), object(), object()]
        fake_chain = object()

        with (
            patch.object(app, "load_documents", return_value=[object()]),
            patch.object(app, "split_documents", return_value=fake_chunks),
            patch.object(app, "build_vector_db", return_value=object()),
            patch.object(app, "build_chain", return_value=fake_chain),
        ):
            app.build_index()

        self.assertIs(app.chain, fake_chain)
        self.assertEqual(app.chunk_count, 3)

    def test_answer_uses_index_built_by_build_index(self):
        app = pdf_rag.PDFRAGApp(pdf_rag.RAGConfig())
        fake_chain = MagicMock()
        fake_chain.invoke.return_value = "an answer"
        app.chain = fake_chain

        self.assertEqual(app.answer("what?"), "an answer")
        fake_chain.invoke.assert_called_once_with(input="what?")

    def test_answer_before_build_index_raises(self):
        app = pdf_rag.PDFRAGApp(pdf_rag.RAGConfig())

        with self.assertRaises(RuntimeError):
            app.answer("what?")

    def test_build_chain_turns_off_model_reasoning_by_default(self):
        app = pdf_rag.PDFRAGApp(pdf_rag.RAGConfig())

        with (
            patch.object(pdf_rag, "ChatOllama") as chat_ollama,
            patch.object(pdf_rag, "MultiQueryRetriever"),
        ):
            app.build_chain(MagicMock())

        chat_ollama.assert_called_once_with(model="gemma4", reasoning=False)

    def test_load_documents_raises_file_not_found_for_missing_pdf(self):
        app = pdf_rag.PDFRAGApp(pdf_rag.RAGConfig(doc_path="/nonexistent/missing.pdf"))

        with self.assertRaises(FileNotFoundError):
            app.load_documents()


class RAGConfigEnvTests(unittest.TestCase):
    def setUp(self):
        self._saved = {key: os.environ.pop(key, None) for key in ENV_KEYS}

    def tearDown(self):
        for key, value in self._saved.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value

    def test_defaults_when_env_unset(self):
        config = pdf_rag.RAGConfig()

        self.assertEqual(config.doc_path, "./data/HOA.pdf")
        self.assertEqual(config.model, "gemma4")
        self.assertEqual(config.embedding_model, "nomic-embed-text")

    def test_env_vars_override_defaults(self):
        os.environ.update(
            RAG_DOC_PATH="/docs/other.pdf",
            RAG_MODEL="llama3",
            RAG_EMBED_MODEL="mxbai-embed-large",
        )

        config = pdf_rag.RAGConfig()

        self.assertEqual(config.doc_path, "/docs/other.pdf")
        self.assertEqual(config.model, "llama3")
        self.assertEqual(config.embedding_model, "mxbai-embed-large")


if __name__ == "__main__":
    unittest.main()
