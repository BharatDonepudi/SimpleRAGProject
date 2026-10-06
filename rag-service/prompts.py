"""
Prompt templates for the PDF RAG pipeline.

Every prompt is a named constant in this file plus a builder function, and
each one is covered by a test that checks its input variables
(see tests/test_prompts.py). Add new prompts the same way.
"""

from langchain_core.prompts import ChatPromptTemplate, PromptTemplate


# Rewrites the user question into several variants for the MultiQueryRetriever.
# Input variables: question
QUERY_REWRITE_TEMPLATE = """You are an AI language model assistant. Your task is to generate five \
different versions of the given user question to retrieve relevant documents from \
a vector database. By generating multiple perspectives on the user question, your \
goal is to help the user overcome some of the limitations of the distance-based \
similarity search. Provide these alternative questions separated by newlines.
Original question: {question}"""

# Guardrail appended to the answer prompt so the model does not invent answers.
SYSTEM_GUARDRAIL = (
    "If the context does not contain the answer, say that the document does not "
    "cover it instead of guessing or inventing an answer."
)

# Answers the question from the retrieved context only.
# Input variables: context, question
RAG_ANSWER_TEMPLATE = (
    "Answer the question based ONLY on the following context. "
    f"{SYSTEM_GUARDRAIL}\n"
    "Context:\n"
    "{context}\n"
    "Question: {question}\n"
)


def query_rewrite_prompt() -> PromptTemplate:
    """Prompt used by MultiQueryRetriever to generate alternative questions."""
    return PromptTemplate(
        input_variables=["question"],
        template=QUERY_REWRITE_TEMPLATE,
    )


def rag_answer_prompt() -> ChatPromptTemplate:
    """Prompt that answers the user question from the retrieved context."""
    return ChatPromptTemplate.from_template(RAG_ANSWER_TEMPLATE)
