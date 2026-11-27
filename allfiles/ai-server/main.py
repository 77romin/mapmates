"""
너랑 갈.지도 AI 서버 (FastAPI + ChromaDB RAG)

- POST /chat      : 질문 -> (문서 검색) -> 생성형 AI 응답. 문서 참고 여부를 함께 반환
- POST /upload    : txt / md / pdf 업로드 -> data 폴더 저장 -> 청킹 -> 임베딩 -> 벡터 DB 저장
- POST /search    : 벡터 유사도 검색만 수행 (Spring AI 의 DocumentTool 이 호출)
- GET  /documents : 업로드된 문서 목록

실행: uvicorn main:app --port 8000
"""
import os
import re
import uuid
from pathlib import Path

import chromadb
import httpx
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from pypdf import PdfReader

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
CHROMA_DIR = BASE_DIR / "chroma_data"
DATA_DIR.mkdir(exist_ok=True)


def load_env():
    """.env 파일을 읽어 환경변수로 등록한다. (python-dotenv 없이 동작)"""
    env_file = BASE_DIR / ".env"
    if not env_file.exists():
        return
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())


load_env()
GMS_KEY = os.environ.get("GMS_KEY", "")
GMS_BASE_URL = os.environ.get("GMS_BASE_URL", "https://gms.ssafy.io/gmsapi/api.openai.com/v1")
CHAT_MODEL = os.environ.get("CHAT_MODEL", "gpt-6-luna")
EMBEDDING_MODEL = os.environ.get("EMBEDDING_MODEL", "text-embedding-3-large")

# RAG 파라미터 (고찰 보고서의 실험 대상)
CHUNK_SIZE = int(os.environ.get("CHUNK_SIZE", "500"))        # 청크 최대 글자 수
CHUNK_OVERLAP = int(os.environ.get("CHUNK_OVERLAP", "80"))   # 청크 간 겹침 글자 수
SIMILARITY_THRESHOLD = float(os.environ.get("SIMILARITY_THRESHOLD", "0.30"))  # 코사인 유사도 기준
TOP_K = int(os.environ.get("TOP_K", "3"))
ALLOWED_EXT = {".txt", ".md", ".pdf"}
MAX_FILE_SIZE = 10 * 1024 * 1024

app = FastAPI(title="mapmates AI server")
app.add_middleware(
    CORSMiddleware,
    allow_origin_regex=r"http://(localhost|127\.0\.0\.1)(:\d+)?",
    allow_methods=["*"],
    allow_headers=["*"],
)

chroma = chromadb.PersistentClient(path=str(CHROMA_DIR))
collection = chroma.get_or_create_collection("documents", metadata={"hnsw:space": "cosine"})
http = httpx.Client(timeout=60)


# ---------- 외부 생성형 AI API ----------
def gms_post(path: str, payload: dict) -> dict:
    if not GMS_KEY:
        raise HTTPException(503, "AI 서버에 GMS_KEY 가 설정되지 않았습니다. (.env 확인)")
    try:
        res = http.post(f"{GMS_BASE_URL}{path}", json=payload, headers={"Authorization": f"Bearer {GMS_KEY}"})
        res.raise_for_status()
        return res.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(502, f"생성형 AI API 오류 ({e.response.status_code})")
    except httpx.HTTPError:
        raise HTTPException(502, "생성형 AI API 에 연결하지 못했습니다.")


def embed(texts: list[str]) -> list[list[float]]:
    data = gms_post("/embeddings", {"model": EMBEDDING_MODEL, "input": texts})
    return [d["embedding"] for d in data["data"]]


def chat_completion(system: str, user: str) -> str:
    data = gms_post("/chat/completions", {
        "model": CHAT_MODEL,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
    })
    return data["choices"][0]["message"]["content"]


# ---------- 문서 처리 ----------
def extract_text(path: Path) -> str:
    if path.suffix.lower() == ".pdf":
        reader = PdfReader(str(path))
        return "\n\n".join((page.extract_text() or "") for page in reader.pages)
    return path.read_text(encoding="utf-8", errors="ignore")


def chunk_text(text: str) -> list[str]:
    """
    문단 우선 청킹: 빈 줄/제목 기준으로 문단을 나누고, CHUNK_SIZE 를 넘지 않게 이어 붙인다.
    한 문단이 너무 길면 글자 수로 자르되 CHUNK_OVERLAP 만큼 겹쳐 문맥이 끊기지 않게 한다.
    """
    text = re.sub(r"\r\n?", "\n", text)
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n|\n(?=#{1,6}\s)", text) if p.strip()]
    chunks, current = [], ""
    for para in paragraphs:
        if len(para) > CHUNK_SIZE:
            if current:
                chunks.append(current)
                current = ""
            step = CHUNK_SIZE - CHUNK_OVERLAP
            chunks.extend(para[i:i + CHUNK_SIZE] for i in range(0, len(para), step))
        elif len(current) + len(para) + 1 <= CHUNK_SIZE:
            current = f"{current}\n{para}" if current else para
        else:
            chunks.append(current)
            current = para
    if current:
        chunks.append(current)
    return chunks


def index_document(path: Path) -> int:
    """문서를 청킹·임베딩해서 벡터 DB 에 저장한다. 같은 파일을 다시 올리면 기존 청크를 교체한다."""
    chunks = chunk_text(extract_text(path))
    collection.delete(where={"source": path.name})
    if not chunks:
        return 0
    for start in range(0, len(chunks), 64):  # 임베딩 API 배치 호출
        batch = chunks[start:start + 64]
        collection.add(
            ids=[f"{path.name}-{start + i}-{uuid.uuid4().hex[:8]}" for i in range(len(batch))],
            documents=batch,
            embeddings=embed(batch),
            metadatas=[{"source": path.name, "chunk": start + i} for i in range(len(batch))],
        )
    return len(chunks)


def search_chunks(query: str, top_k: int) -> list[dict]:
    """질문을 벡터로 바꿔 유사한 청크를 찾고, 유사도 기준을 넘는 것만 반환한다."""
    if collection.count() == 0:
        return []
    result = collection.query(query_embeddings=embed([query]), n_results=min(top_k, collection.count()))
    hits = []
    for text, meta, distance in zip(result["documents"][0], result["metadatas"][0], result["distances"][0]):
        score = round(1 - distance, 4)  # cosine distance -> similarity
        if score >= SIMILARITY_THRESHOLD:
            hits.append({"text": text, "source": meta["source"], "chunk": meta["chunk"], "score": score})
    return hits


# ---------- API ----------
class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=1000)


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=1000)
    top_k: int = Field(default=TOP_K, ge=1, le=10)


@app.post("/chat")
def chat(req: ChatRequest):
    hits = search_chunks(req.message, TOP_K)
    if hits:
        context = "\n\n".join(f"[{h['source']}] {h['text']}" for h in hits)
        system = ("너는 여행 서비스 '너랑 갈.지도'의 문서 기반 도우미다. 아래 참고 문서의 내용만 근거로 한국어로 답하라. "
                  "문서에 없는 내용은 모른다고 답하라.\n\n[참고 문서]\n" + context)
        answer = chat_completion(system, req.message)
    else:
        system = "너는 여행 서비스 '너랑 갈.지도'의 도우미다. 한국어로 간결하게 답하라."
        answer = chat_completion(system, req.message)
    return {
        "answer": answer,
        "used_documents": bool(hits),  # 문서 참고 여부
        "notice": "업로드된 문서를 참고한 답변입니다." if hits else "참고할 문서를 찾지 못해 일반 생성형 AI 답변을 제공합니다.",
        "sources": [{"source": h["source"], "score": h["score"], "preview": h["text"][:120]} for h in hits],
    }


@app.post("/search")
def search(req: SearchRequest):
    return {"results": search_chunks(req.query, req.top_k)}


@app.post("/upload")
async def upload(file: UploadFile = File(...)):
    name = Path(file.filename or "").name  # 경로 제거
    ext = Path(name).suffix.lower()
    if ext not in ALLOWED_EXT:
        raise HTTPException(400, "txt, md, pdf 파일만 업로드할 수 있습니다.")
    safe_name = re.sub(r"[^\w가-힣.\- ]", "_", name)
    content = await file.read()
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(400, "파일 크기는 10MB 이하여야 합니다.")
    path = DATA_DIR / safe_name
    path.write_bytes(content)
    count = index_document(path)
    return {"filename": safe_name, "chunks": count, "message": f"{safe_name} 문서를 {count}개 청크로 저장했습니다."}


@app.get("/documents")
def documents():
    files = sorted(p.name for p in DATA_DIR.iterdir() if p.suffix.lower() in ALLOWED_EXT)
    return {"documents": files, "chunks": collection.count()}


@app.get("/health")
def health():
    return {"status": "ok", "chunks": collection.count(), "key_configured": bool(GMS_KEY)}


@app.on_event("startup")
def index_existing():
    """서버 시작 시 data 폴더의 문서 중 아직 색인되지 않은 것을 색인한다."""
    if not GMS_KEY:
        return
    for path in DATA_DIR.iterdir():
        if path.suffix.lower() in ALLOWED_EXT and not collection.get(where={"source": path.name}, limit=1)["ids"]:
            try:
                index_document(path)
            except Exception as e:  # 색인 실패가 서버 기동을 막지 않게 한다
                print(f"색인 실패: {path.name} ({e})")
