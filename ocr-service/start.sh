#!/bin/bash
# ============================================================================
# LexAgent OCR 微服务启动脚本（Linux/Mac）
# ============================================================================
set -e
cd "$(dirname "$0")"

echo "============================================"
echo " LexAgent OCR 微服务（PaddleOCR + FastAPI）"
echo "============================================"

# 优先使用虚拟环境
if [ -f "venv/bin/python" ]; then
    PYTHON="venv/bin/python"
    echo "使用虚拟环境 venv"
else
    PYTHON="python3"
    echo "使用系统 Python3"
fi

# 检查依赖
if ! $PYTHON -c "import paddleocr, fastapi, fitz" 2>/dev/null; then
    echo "[警告] 依赖未安装，请先执行："
    echo "    pip install -r requirements.txt"
    exit 1
fi

echo ""
echo "启动 OCR 服务 http://localhost:8501"
echo "健康检查：http://localhost:8501/health"
echo "按 Ctrl+C 停止"
echo ""

exec $PYTHON server.py
