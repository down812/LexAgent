# LexAgent OCR 微服务

基于 **PaddleOCR 3.x + FastAPI** 的中文法律文本 OCR 识别服务，作为 Spring Boot 主项目的旁路微服务，处理扫描版 PDF 和图片法律文档。

## 目录结构

```
ocr-service/
├── server.py           FastAPI 入口 + 路由
├── ocr_engine.py       PaddleOCR 3.x 引擎封装
├── text_cleaner.py    OCR 文本清洗（段落合并、噪声去除）
├── requirements.txt    Python 依赖（不含 paddlepaddle，见安装说明）
├── install.bat         Windows 一键安装脚本
├── install.sh          Linux/Mac 一键安装脚本
├── start.bat          Windows 启动脚本
├── start.sh           Linux/Mac 启动脚本
└── README.md          本文件
```

## 环境要求

- **Python 3.9 ~ 3.13**（PaddlePaddle 3.x 支持 3.13，3.x 安装包面向 3.9-3.13）
- 内存 ≥ 4 GB（PaddleOCR 模型加载）
- 首次运行会自动下载 PaddleOCR 中文模型（约 100 MB），需联网

## 安装（重要：分两步）

### 为什么 paddlepaddle 不在 requirements.txt 里？

**PyPI 上的 `paddlepaddle` 是旧版 2.6.x，不支持 Python 3.13**（会报 `No matching distribution found`）。
**PaddlePaddle 3.x 只在百度官方源发布**，必须用 `-i` 指定官方源安装。

### 第 1 步：安装 PaddlePaddle 3.x（必须用官方源）

```bash
# CPU 版（支持 Python 3.9-3.13）
pip install paddlepaddle==3.2.1 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/

# GPU 版（CUDA 12.6，需显卡驱动 ≥550.54.14）
pip install paddlepaddle-gpu==3.2.1 -i https://www.paddlepaddle.org.cn/packages/stable/cu126/
```

> ⚠️ **不要** `pip install paddlepaddle`（默认从 PyPI 找，只有 2.6.x，不支持 Python 3.13）

### 第 2 步：安装其余依赖

```bash
cd ocr-service
pip install -r requirements.txt
```

### 一键安装（推荐）

```bash
cd ocr-service
# Windows:
install.bat
# Linux/Mac:
./install.sh
```

脚本会自动完成两步安装。建议先创建虚拟环境：

```bash
python -m venv venv
# Windows:
venv\Scripts\activate
# Linux/Mac:
source venv/bin/activate
# 然后运行 install.bat / install.sh
```

## 启动

```bash
# 方式一：启动脚本
# Windows:
start.bat
# Linux/Mac:
./start.sh

# 方式二：手动启动
python server.py

# 方式三：uvicorn（支持热重载，开发用）
uvicorn server:app --host 0.0.0.0 --port 8501 --reload
```

启动成功后访问：
- 健康检查：http://localhost:8501/health
- 接口文档：http://localhost:8501/docs

## API 接口

### 1. 健康检查

```
GET /health
```

返回：`{"status": "ok", "engine": "paddleocr", "loaded": true}`

### 2. 识别单张图片

```
POST /ocr/recognize
Content-Type: multipart/form-data

file: 图片文件（jpg/png）
```

返回：

```json
{
  "text": "识别出的文本...",
  "pages": 1,
  "length": 123
}
```

### 3. 识别扫描版 PDF

```
POST /ocr/recognize_pdf?dpi=200
Content-Type: multipart/form-data

file: PDF 文件
```

返回：

```json
{
  "text": "拆页 + 逐页 OCR 合并后的文本...",
  "pages": 10,
  "length": 5678
}
```

参数：
- `dpi`（可选）：PDF 转图片的分辨率，默认 200，范围 100-400。值越大越清晰但越慢。

## 与 Spring Boot 主项目集成

主项目（`LexAgent`）通过 `OcrClient`（Hutool HttpUtil）调用本服务：

| 场景 | 调用路径 |
|---|---|
| 上传图片（jpg/png） | `DocumentParseService` → `OcrService.ocrImage()` → `POST /ocr/recognize` |
| 上传扫描版 PDF | `DocumentParseService.parsePdf()` 检测文本量过少 → `OcrService.ocrPdf()` → `POST /ocr/recognize_pdf` |

OCR 服务地址在 `application-rag.yaml` 中配置：

```yaml
lexagent:
  rag:
    ocr:
      base-url: http://localhost:8501
      enabled: true
```

## 文本清洗策略（法律场景）

PaddleOCR 按行输出，本服务做了专门的段落重建：

1. **段落起点识别**：以「第X条」「第X章」「（一）」「1.」等开头的行视为新段落起点
2. **段内合并**：同段落的连续 OCR 行直接拼接（中文场景去除空格）
3. **整体清洗**：去除零宽字符、不间断空格、孤立标点行、压缩空行

这使得 OCR 输出的文本能直接进入 `LegalStructureService` 的法律条款切分流程，与文本型 PDF/DOCX 走同一套结构化逻辑。

## 性能参考

| 硬件 | 单页 OCR 耗时 |
|---|---|
| CPU 8 核 / 16 GB | 1-2 秒 |
| CPU 4 核 / 8 GB | 3-5 秒 |
| GPU（RTX 3060） | <0.5 秒 |

首次请求会触发模型加载（约 5-10 秒），后续请求复用模型。

## 常见问题

### 1. ERROR: No matching distribution found for paddlepaddle==2.6.1

**原因**：你用的是 Python 3.13，而 PaddlePaddle 2.6.x 不支持 Python 3.13。

**解决**：升级到 PaddlePaddle 3.x（支持 Python 3.9-3.13），用百度官方源安装：

```bash
pip install paddlepaddle==3.2.1 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/
```

不要用 `pip install paddlepaddle`（默认从 PyPI 找，只有 2.6.x）。

### 2. PaddleOCR 2.x 代码报错：Unknown argument: use_angle_cls / use_gpu

**原因**：PaddleOCR 3.x 与 2.x API 不兼容。

**迁移对照**：

| 2.x | 3.x |
|---|---|
| `ocr.ocr(img, cls=True)` | `ocr.predict(img)` |
| `use_angle_cls=True` | `use_textline_orientation=True` |
| `use_gpu=True` | `device="gpu"` |
| `show_log=False` | 移除（用 logging） |
| `[[box, (text, score)], ...]` | `res["rec_texts"]` / `res["rec_scores"]` / `res["rec_boxes"]` |

本服务的 `ocr_engine.py` 已完成 3.x 适配。

### 3. 模型下载失败

PaddleOCR 3.x 首次运行会从 Hugging Face 下载模型，若网络不通：
- 配置代理：`set HTTPS_PROXY=http://your-proxy:port`
- 或使用镜像：`set HF_ENDPOINT=https://hf-mirror.com`

### 4. 内存不足

降低 DPI（`recognize_pdf?dpi=150`），或限制 PDF 页数。

### 5. 网络慢，paddlepaddle 下载不动

paddlepaddle 3.0.0 CPU 包约 200 MB。官方源是国内 CDN，正常比 PyPI 快。若仍慢：
- 检查是否真的用了官方源（`-i https://www.paddlepaddle.org.cn/packages/stable/cpu/`）
- 不要用清华源装 paddlepaddle 3.x（清华源可能只有 2.x）
