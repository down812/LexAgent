"""
PaddleOCR 3.x 引擎封装

适配 PaddleOCR 3.x API（与 2.x 不兼容）：
    - 实例化：PaddleOCR(lang="ch", use_textline_orientation=True)
    - 调用：ocr.predict(img)  返回结果对象列表
    - 字段：res["rec_texts"] / res["rec_scores"] / res["rec_boxes"]

针对法律扫描件优化：
    - 关闭整页方向矫正（use_doc_orientation_classify=False）：扫描件方向正确，省时
    - 关闭弯曲矫正（use_doc_unwarping=False）：扫描件平整，省时
    - 保留单行方向矫正（use_textline_orientation=True）：处理偶发倒置行
"""
import logging
from typing import List, Tuple

from paddleocr import PaddleOCR

logger = logging.getLogger("lexagent-ocr.engine")


class OcrEngine:
    """PaddleOCR 3.x 引擎单例封装"""

    def __init__(self):
        logger.info("初始化 PaddleOCR 3.x 引擎（首次调用会下载模型，请稍候）...")
        self._ocr = PaddleOCR(
            lang="ch",                           # 中文识别
            device="cpu",                        # CPU 推理（GPU 用户改 "gpu" 或 "gpu:0"）
            use_doc_orientation_classify=False,  # 扫描件方向正确，关闭省时
            use_doc_unwarping=False,             # 扫描件平整，关闭省时
            use_textline_orientation=True,       # 保留单行方向矫正
        )
        logger.info("PaddleOCR 3.x 引擎就绪")

    def recognize(self, image) -> List[str]:
        """
        识别 PIL 图像，返回按阅读顺序排列的文本行列表

        :param image: PIL.Image.Image
        :return: 文本行列表，已按纵坐标排序
        """
        import numpy as np
        img_array = np.array(image)

        # 3.x: predict() 返回结果对象列表（每个对应一页/一张图）
        results = self._ocr.predict(img_array)
        if not results:
            return []

        lines: List[Tuple[float, str, float]] = []
        for res in results:
            # 3.x 结果对象：字典式访问
            # 注意：res.get(...) 返回的是 numpy array，不能用 `or []` 短路（会触发 ambiguous 真值错误）
            texts = res.get("rec_texts")
            scores = res.get("rec_scores")
            boxes = res.get("rec_boxes")
            if texts is None or scores is None or boxes is None:
                continue
            # 统一转 list，避免 numpy array 在后续 zip/迭代中的边界问题
            texts = list(texts)
            scores = list(scores)
            boxes = list(boxes)
            if not texts:
                continue

            for text, score, box in zip(texts, scores, boxes):
                if text is None or not str(text).strip():
                    continue
                # box 格式：[x1, y1, x2, y2]（水平外接框，左上角 + 右下角）
                y_top = float(box[1])
                lines.append((y_top, str(text).strip(), float(score)))

        # 按纵坐标排序（容差 10px 视为同一行，按 x 排）
        lines.sort(key=lambda x: (round(x[0] / 10), x[0]))

        out: List[str] = []
        for _, text, score in lines:
            if score < 0.5:
                logger.debug("低置信度行(%.2f): %s", score, text)
            out.append(text)
        return out
