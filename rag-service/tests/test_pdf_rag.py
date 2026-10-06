import importlib.util
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


MODULE_PATH = Path(__file__).resolve().parents[1] / "pdf_rag.py"


def load_pdf_rag_module():
    spec = importlib.util.spec_from_file_location("pdf_rag", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class PDFRAGTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pdf_rag = load_pdf_rag_module()

    def test_run_preserves_pipeline_flow(self):
        config = self.pdf_rag.RAGConfig(question="test question")
        app = self.pdf_rag.PDFRAGApp(config)
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
        with patch.object(self.pdf_rag.PDFRAGApp, "run", return_value="ok") as run_mock:
            response = self.pdf_rag.main()

        self.assertEqual(response, "ok")
        run_mock.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
