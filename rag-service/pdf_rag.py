"""
Simple PDF RAG script.

Run `pip install -r requirements.txt` to install dependencies.
"""

from dataclasses import dataclass
from pathlib import Path

import ollama
from langchain_classic.retrievers import MultiQueryRetriever
from langchain_community.document_loaders import PyPDFLoader
from langchain_community.vectorstores import Chroma
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate, PromptTemplate
from langchain_core.runnables import RunnablePassthrough
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter


@dataclass(frozen=True)
class RAGConfig:
    doc_path: str = "./data/HOA.pdf"
    model: str = "gemma4"
    embedding_model: str = "nomic-embed-text"
    collection_name: str = "simple-rag"
    chunk_size: int = 1200
    chunk_overlap: int = 300
    question: str = "What are the main points that I should refer to first"


class PDFRAGApp:
    def __init__(self, config: RAGConfig) -> None:
        self.config = config
        self.pdf_file = Path(config.doc_path)

    def load_documents(self):
        if not self.pdf_file.exists():
            print("upload a pdf file")
            raise SystemExit(1)

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

    def create_query_prompt(self) -> PromptTemplate:
        return PromptTemplate(
            input_variables=["question"],
            template=""" You are an AI language model assistant. Your task is to generate five
    different versions of the given user question to retrieve relevant documents from
    a vector database. By generating multiple perspectives on the user question, your
    goal is to help the user overcome some of the limitations of the distance-based
    similarity search. Provide these alternative questions separated by newlines.
    Original question: {question} """,
        )

    def create_rag_prompt(self) -> ChatPromptTemplate:
        template = """Answer the question based ONLY on the following context:
{context}
Question: {question}
"""
        return ChatPromptTemplate.from_template(template)

    def build_chain(self, vector_db):
        llm = ChatOllama(model=self.config.model)
        retriever = MultiQueryRetriever.from_llm(
            vector_db.as_retriever(),
            llm,
            prompt=self.create_query_prompt(),
        )
        prompt = self.create_rag_prompt()

        return (
            {"context": retriever, "question": RunnablePassthrough()}
            | prompt
            | llm
            | StrOutputParser()
        )

    def run(self) -> str:
        documents = self.load_documents()
        chunks = self.split_documents(documents)
        vector_db = self.build_vector_db(chunks)
        chain = self.build_chain(vector_db)
        response = chain.invoke(input=self.config.question)
        print(response)
        return response


def main() -> str:
    app = PDFRAGApp(RAGConfig())
    return app.run()


if __name__ == "__main__":
    main()
