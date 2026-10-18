@echo off
REM ============================================================================
REM LexAgent OCR service - start script (Windows)
REM Usage:
REM   1. First time: run install.bat (or pip install -r requirements.txt)
REM   2. Double-click start.bat to launch
REM ============================================================================
cd /d "%~dp0"

echo ============================================
echo  LexAgent OCR service (PaddleOCR + FastAPI)
echo ============================================
echo.

REM Prefer virtualenv venv, fall back to system python
if exist "venv\Scripts\python.exe" (
    echo Using virtualenv venv
    set "PYTHON=venv\Scripts\python.exe"
) else (
    echo Using system Python
    set "PYTHON=python"
)

REM Check dependencies
%PYTHON% -c "import paddleocr, fastapi, fitz" 2>nul
if errorlevel 1 (
    echo [WARN] Dependencies not installed. Please run first:
    echo     install.bat
    echo.
    pause
    exit /b 1
)

echo.
echo Starting OCR service http://localhost:8501
echo Health check: http://localhost:8501/health
echo Press Ctrl+C to stop
echo.

%PYTHON% server.py

pause
