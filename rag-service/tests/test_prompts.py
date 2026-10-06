import sys
import unittest
from pathlib import Path

RAG_SERVICE_DIR = Path(__file__).resolve().parents[1]
if str(RAG_SERVICE_DIR) not in sys.path:
    sys.path.insert(0, str(RAG_SERVICE_DIR))

import prompts  # noqa: E402


class QueryRewritePromptTests(unittest.TestCase):
    def test_input_variables(self):
        prompt = prompts.query_rewrite_prompt()

        self.assertEqual(sorted(prompt.input_variables), ["question"])

    def test_template_constant_matches_builder(self):
        self.assertEqual(prompts.query_rewrite_prompt().template, prompts.QUERY_REWRITE_TEMPLATE)

    def test_formats_without_error(self):
        text = prompts.query_rewrite_prompt().format(question="What is the HOA fee?")

        self.assertIn("What is the HOA fee?", text)


class RagAnswerPromptTests(unittest.TestCase):
    def test_input_variables(self):
        prompt = prompts.rag_answer_prompt()

        self.assertEqual(sorted(prompt.input_variables), ["context", "question"])

    def test_template_has_guardrail(self):
        self.assertIn(prompts.SYSTEM_GUARDRAIL, prompts.RAG_ANSWER_TEMPLATE)
        self.assertIn("does not", prompts.SYSTEM_GUARDRAIL)

    def test_formats_without_error(self):
        messages = prompts.rag_answer_prompt().format_messages(
            context="ctx text", question="q text"
        )
        text = messages[0].content

        self.assertIn("ctx text", text)
        self.assertIn("q text", text)


if __name__ == "__main__":
    unittest.main()
