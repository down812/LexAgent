#!/bin/bash
# ============================================================================
# LexAgent OCR 微服务依赖一键安装脚本（Linux/Mac）
# 解决 Python 3.13 安装 paddlepaddle 报错问题：
#   PyPI 上的 paddlepaddle 是旧版 2.6.x 不支持 Python 3.13
#   必须用百度官方源安装 PaddlePaddle 3.x
# ============================================================================
set -e
cd "$(dirname "$0")"

echo "============================================"
echo " LexAgent OCR 微服务依赖安装"
echo "============================================"

# 优先使用虚拟环境
if [ -f "venv/bin/python" ]; then
    PYTHON="venv/bin/python"
    PIP="venv/bin/pip"
    echo "[1] 使用虚拟环境 venv"
else
    PYTHON="python3"
    PIP="pip3"
    echo "[1] 使用系统 Python3"
fi

if ! $PYTHON --version 2>/dev/null; then
    echo "[错误] 未找到 Python，请先安装 Python 3.9-3.13"
    exit 1
fi

echo ""
echo "[2] Python 版本："
$PYTHON --version
echo ""

echo "============================================"
echo " 第 1 步：安装 PaddlePaddle 3.x（CPU 版）"
echo "============================================"
echo "重要：必须用百度官方源，PyPI 上的 paddlepaddle 不支持 Python 3.13"
echo ""

$PIP install paddlepaddle==3.0.0 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/

echo ""
echo "============================================"
echo " 第 2 步：安装其余依赖（PaddleOCR + FastAPI 等）"
echo "============================================"

$PIP install -r requirements.txt

echo ""
echo "============================================"
echo " 验证安装"
echo "============================================"
$PYTHON -c "import paddle; print('PaddlePaddle', paddle.__version__)"
$PYTHON -c "from paddleocr import PaddleOCR; print('PaddleOCR import OK')"
$PYTHON -c "import fastapi, fitz; print('FastAPI + PyMuPDF OK')"

echo ""
echo "============================================"
echo " 安装完成！"
echo "============================================"
echo "启动 OCR 服务：./start.sh"
echo "健康检查：http://localhost:8501/health"
