"""
LexAgent OCR 微服务
基于 PaddleOCR 的中文法律文本 OCR 识别服务

功能：
    1. POST /ocr/recognize       —— 识别单张图片中的中文文本
    2. POST /ocr/recognize_pdf   —— 拆页 + OCR 识别扫描版 PDF
    3. GET  /health              —— 健康检查

调用方：Spring Boot 端 OcrClient（HTTP）

启动：python server.py   或   uvicorn server:app --host 0.0.0.0 --port 8501
"""
import io
import logging
from typing import List, Tuple

import fitz  # PyMuPDF
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from PIL import Image

from ocr_engine import OcrEngine
from text_cleaner import TextCleaner

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("lexagent-ocr")

app = FastAPI(title="LexAgent OCR Service", version="1.0.0")

# 全局 OCR 引擎（懒加载，首次调用初始化，避免启动即下载模型）
_ocr_engine: OcrEngine = None


def get_ocr_engine() -> OcrEngine:
    global _ocr_engine
    if _ocr_engine is None:
        logger.info("初始化 PaddleOCR 引擎（首次调用会下载模型，请稍候）...")
        _ocr_engine = OcrEngine()
        logger.info("PaddleOCR 引擎就绪")
    return _ocr_engine


def _image_to_pil(image_bytes: bytes) -> Image.Image:
    """字节流 -> PIL 图像"""
    try:
        return Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"无法解析图片: {e}")


def _render_pdf_to_images(pdf_bytes: bytes, dpi: int = 200) -> List[Image.Image]:
    """PDF 拆页 -> PIL 图像列表（默认 200 DPI，兼顾清晰度与速度）"""
    images: List[Image.Image] = []
    try:
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"无法解析 PDF: {e}")

    zoom = dpi / 72  # PyMuPDF 默认 72 DPI
    matrix = fitz.Matrix(zoom, zoom)
    for page_idx in range(doc.page_count):
        page = doc[page_idx]
        pix = page.get_pixmap(matrix=matrix)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        images.append(img)
        logger.info("PDF 拆页 [%d/%d] 完成 %dx%d", page_idx + 1, doc.page_count, img.width, img.height)
    doc.close()
    return images


def _recognize_images(images: List[Image.Image]) -> str:
    """对图片列表逐张 OCR，拼接为整段文本"""
    engine = get_ocr_engine()
    page_texts: List[str] = []
    for idx, img in enumerate(images, 1):
        logger.info("OCR 识别第 %d/%d 页", idx, len(images))
        try:
            lines = engine.recognize(img)
        except Exception as e:
            logger.error("第 %d 页 OCR 失败: %s", idx, e)
            lines = []
        page_text = TextCleaner.merge_lines(lines)
        page_texts.append(page_text)
    full = "\n\n".join(t for t in page_texts if t)
    return TextCleaner.post_clean(full)


@app.get("/health")
def health():
    """健康检查"""
    return {"status": "ok", "engine": "paddleocr", "loaded": _ocr_engine is not None}


@app.post("/ocr/recognize")
async def recognize(file: UploadFile = File(...)):
    """识别单张图片中的中文文本"""
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="仅支持图片文件")
    img_bytes = await file.read()
    if not img_bytes:
        raise HTTPException(status_code=400, detail="上传文件为空")

    img = _image_to_pil(img_bytes)
    engine = get_ocr_engine()
    try:
        lines = engine.recognize(img)
    except Exception as e:
        logger.error("OCR 识别失败", exc_info=True)
        raise HTTPException(status_code=500, detail=f"OCR 识别失败: {e}")

    text = TextCleaner.merge_lines(lines)
    text = TextCleaner.post_clean(text)
    logger.info("图片 OCR 完成，文本长度 %d", len(text))
    return JSONResponse({"text": text, "pages": 1, "length": len(text)})


@app.post("/ocr/recognize_pdf")
async def recognize_pdf(file: UploadFile = File(...), dpi: int = 200):
    """扫描版 PDF：拆页 -> 逐页 OCR -> 合并文本"""
    if not file.filename or not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="仅支持 PDF 文件")
    pdf_bytes = await file.read()
    if not pdf_bytes:
        raise HTTPException(status_code=400, detail="上传文件为空")

    logger.info("收到 PDF: %s, 大小 %.2f KB, DPI=%d", file.filename, len(pdf_bytes) / 1024, dpi)
    images = _render_pdf_to_images(pdf_bytes, dpi=dpi)
    if not images:
        raise HTTPException(status_code=400, detail="PDF 无可识别页面")

    text = _recognize_images(images)
    logger.info("PDF OCR 完成，共 %d 页，文本长度 %d", len(images), len(text))
    return JSONResponse({"text": text, "pages": len(images), "length": len(text)})


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8501, reload=False)
