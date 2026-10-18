@echo off
REM ============================================================================
REM LexAgent OCR service - dependency install script (Windows)
REM ----------------------------------------------------------------------------
REM Fixes Python 3.13 paddlepaddle install error:
REM   PyPI only has paddlepaddle 2.6.x which does NOT support Python 3.13
REM   PaddlePaddle 3.2.1 (supports Python 3.9-3.13) is only on Baidu official index
REM   PaddlePaddle 3.0.0 is TOO OLD: causes "strides" error with PaddleOCR 3.7.0 models
REM ============================================================================
cd /d "%~dp0"

echo ============================================
echo  LexAgent OCR service - install dependencies
echo ============================================
echo.

REM Prefer virtualenv venv, fall back to system python
if exist "venv\Scripts\python.exe" (
    echo [1] Using virtualenv venv
    set "PYTHON=venv\Scripts\python.exe"
    set "PIP=venv\Scripts\pip.exe"
) else (
    echo [1] Using system Python
    where python >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] Python not found. Please install Python 3.9-3.13 first.
        pause
        exit /b 1
    )
    set "PYTHON=python"
    set "PIP=pip"
)

echo.
echo [2] Python version:
%PYTHON% --version
echo.

echo ============================================
echo  Step 1: Install PaddlePaddle 3.x (CPU)
echo ============================================
echo IMPORTANT: must use Baidu official index. PyPI paddlepaddle does not support Python 3.13
echo.

%PIP% install paddlepaddle==3.2.1 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/
if errorlevel 1 (
    echo.
    echo [ERROR] PaddlePaddle install failed.
    echo Check network, or run manually:
    echo     pip install paddlepaddle==3.2.1 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Step 2: Install other deps (PaddleOCR + FastAPI etc.)
echo ============================================

%PIP% install -r requirements.txt
if errorlevel 1 (
    echo.
    echo [ERROR] Dependency install failed.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Verify install
echo ============================================
%PYTHON% -c "import paddle; print('PaddlePaddle', paddle.__version__)"
%PYTHON% -c "from paddleocr import PaddleOCR; print('PaddleOCR import OK')"
%PYTHON% -c "import fastapi, fitz; print('FastAPI + PyMuPDF OK')"

echo.
echo ============================================
echo  Install complete!
echo ============================================
echo Start OCR service: start.bat
echo Health check: http://localhost:8501/health
echo.
pause
