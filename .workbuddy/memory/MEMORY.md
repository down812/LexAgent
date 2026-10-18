# LexAgent 项目长期记忆

## 项目概况
基于多 Agent 协作与 RAG 的智能法律援助系统（毕设）。Spring Boot 3.4.8 + Java 17 + MyBatis-Plus + MySQL + Redis + JWT。

## RAG 知识库技术选型（阶段一+二已定）
- Spring AI 1.0.0（spring-ai-bom:1.0.0）
- Embedding：阿里云 DashScope text-embedding-v3，1024 维（spring-ai-alibaba-starter-dashscope:1.0.0.4）
- 向量库：Elasticsearch（spring-ai-starter-vector-store-elasticsearch，client 为 elasticsearch-java 8.15.5）
- LLM：DeepSeek（后续多 Agent 阶段接入）
- 文档解析：PDFBox 3.0.3 / Apache POI 5.3.0
- OCR 微服务（阶段二）：独立 Python 子项目 `ocr-service/`，FastAPI + PaddleOCR 3.x + PaddlePaddle 3.x + PyMuPDF

## 关键约定
- RAG 配置独立放 application-rag.yaml（profile=rag），主 application.yaml（含敏感信息）需手动加 `spring.profiles.active: rag`
- DashScope API Key 通过环境变量 AI_DASHSCOPE_API_KEY 注入
- 法律文本按"条款"切分（非定长），保留 法律名+章节+条款号+正文 结构
- 向量数据存 ES，结构化条款同步存 MySQL（legal_laws/legal_articles）供核验

## 环境注意
- 系统 JAVA_HOME=D:\JAVA\jdk1.8（Java 8），命令行构建必须显式指定 JDK 17+：用 C:\Users\asus\.jdks\openjdk-22.0.2
- 用户本地 ES 实际为 9.4.1（D:\develop\elasticsearch-9.4.1），与 Spring AI 8.x client 兼容性待验证，必要时降级 ES 到 8.x
- 用户系统 Python 是 3.13.5，**PaddlePaddle 2.6.x 不支持 Python 3.13**，必须用 PaddlePaddle 3.0.0+
- PaddlePaddle 3.x 只在百度官方源发布：`pip install paddlepaddle==3.2.1 -i https://www.paddlepaddle.org.cn/packages/stable/cpu/`（PyPI 上只有 2.6.x）
- **PaddleOCR 3.7.0 必须配 PaddlePaddle 3.2.1**（paddleocr.dev 官方推荐）：3.0.0 太旧会报 `Type of attribute: strides is not right`（PaddleOCR 3.7 把模型参数从 Int64 改成 Int32，3.0.0 推理引擎无法解析新格式）
- PaddleOCR 3.x 与 2.x API 不兼容：`ocr.ocr(img, cls=True)` → `ocr.predict(img)`，结果用 `res["rec_texts"]/rec_scores/rec_boxes`
- **PaddleOCR 3.x 结果解析禁止用 `x or default` 短路**：`res.get(...)` 返回 numpy array，`or` 会触发 `ValueError: truth value of an array is ambiguous`。必须显式 `if x is None` 判断 + `list()` 转换。同类坑适用于所有返回 numpy array 的库（PaddlePaddle/sklearn/torch 等）
- PyMuPDF 必须 1.24.12+：1.24.10 不支持 Python 3.13（无预编译 wheel，会从源码编译失败需 VS Build Tools）；1.24.12 起支持 3.13 并用 Stable ABI（ocr-service 用 1.24.14）
- OCR 微服务端口 8501，Java 端 OcrClient 通过 application-rag.yaml 的 `lexagent.rag.ocr.base-url` 配置
- **PDF OCR 架构**：Java 端用 PDFBox `PDFRenderer` 拆页转图片 → 逐页调 `/ocr/recognize`（单图 OCR）→ 合并文本。**不要把整个 PDF 传给 Python**（单请求耗时无上限，多页必超时）。read-timeout-ms 默认 60000（单页 3-5 秒，60 秒兜底）
- **Windows .bat/.ps1 脚本必须纯 ASCII**：cmd 默认 GBK 解码，UTF-8 中文会乱码导致命令执行失败（chcp 65001 执行后才生效，来不及）。要中文输出用 Python 脚本。ocr-service 的 install.bat / start.bat 已全部改英文

## 代码结构约定
- 分层：controller / service(+impl) / mapper / entity / dto / vo / result / exception / config / interceptor / constants / Enum / utils
- 统一返回 Result<T>，分页 PageResult(total,pages,pageNum,pageSize,rows)
- 异常 BusinessException(String) / BusinessNotFoundException(String) / PermissionDeniedException(String)
- 拦截器链：WebMvcConfig 注册 JwtInterceptor(order1) → LoginInterceptor(order2)，LoginContext 存登录态
- 知识库写操作限管理员（type 0/1）

## 阶段三·混合检索（已完成）
- Hybrid Search = 向量召回(VectorStore) + 关键词召回(ElasticsearchClient match query) → RRF 融合(k=60)
- Reranker = 法律场景多特征加权：条款号精确匹配×3.0 / 法律名匹配×1.5 / 关键词覆盖率×2.0 / 内容长度归一化
  - 设计考量：纯算法不依赖 LLM，稳定可复现，论文可讲"面向法律场景的多特征加权重排"
- 条款校验 = MySQL legal_articles 真实性核验（法律名+条款号精确/模糊匹配 + 内容相似度防漂移），verified 字段防幻觉
- ElasticsearchClient 由 Spring Boot 自动配置（依赖 elasticsearch-java 8.15.5，spring-ai-starter-vector-store-elasticsearch 传递引入）
- 接口：`POST /knowledge/search/hybrid`（对比 `/search` 基础向量检索）
- 召回扩大倍数 4（topK×4 召回，给 rerank 更多候选）
