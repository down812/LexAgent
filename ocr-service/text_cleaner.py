"""
OCR 文本清洗

法律场景特别需要：PaddleOCR 按行输出，需要合并成段落结构，
并修复 OCR 常见问题（断词、空格、标点）。

清洗策略：
    1. merge_lines：按行合并，识别段落边界
       - 段落起点特征：以「第X条」「第X章」「第X节」「（一）」等开头
       - 同段落内的换行合并为空格（中文场景去空格）
    2. post_clean：整体清洗
       - 统一换行
       - 去除零宽字符、不间断空格
       - 中文之间多余空格压缩
       - 压缩连续空行
"""
import re
from typing import List

# 段落起点正则：以「第X条」「第X章」「第X节」「（一）」「1.」「一、」等开头
_PARAGRAPH_START = re.compile(
    r"^(?:第[一二三四五六七八九十百千零〇0-9]+[条章节款项编])"
    r"|(?:^[（(][一二三四五六七八九十0-9]+[)）])"
    r"|(?:^[一二三四五六七八九十、])"
    r"|(?:^\d+[.、])"
)

# 中文字符范围
_CJK = r"\u4e00-\u9fa5"


class TextCleaner:
    """OCR 文本清洗工具"""

    @staticmethod
    def merge_lines(lines: List[str]) -> str:
        """
        将 OCR 行列表合并为段落文本

        规则：
            - 遇到段落起点（第X条等）开新行
            - 连续的中文行合并去换行（OCR 把一段拆成多行）
            - 行尾是标点保留，否则加空格
        """
        if not lines:
            return ""

        out_paragraphs: List[List[str]] = []
        current: List[str] = []

        for raw in lines:
            line = raw.strip()
            if not line:
                continue
            if _PARAGRAPH_START.match(line):
                # 段落起点：开新段落
                if current:
                    out_paragraphs.append(current)
                current = [line]
            else:
                if not current:
                    current = [line]
                else:
                    current.append(line)

        if current:
            out_paragraphs.append(current)

        # 段落内合并：中文之间不留空格
        result_blocks: List[str] = []
        for para_lines in out_paragraphs:
            merged = "".join(para_lines)
            # 中文与中文之间的多余空格去除
            merged = re.sub(rf"(?<=[{_CJK}])\s+(?=[{_CJK}])", "", merged)
            # 中文与英文/数字之间保留单空格
            merged = re.sub(r"\s{2,}", " ", merged)
            result_blocks.append(merged.strip())

        return "\n\n".join(b for b in result_blocks if b)

    @staticmethod
    def post_clean(text: str) -> str:
        """
        整体清洗：统一换行、去除噪声字符、压缩空行
        """
        if not text:
            return ""
        return (
            text.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u00a0", " ")
            .replace("\u200b", "")
            .replace("\ufeff", "")
            # 去除 OCR 残留的孤立符号行（如仅含「，」「。」）
            .replace("\n,\n", "\n")
            .replace("\n。\n", "\n")
            # 连续空行压缩为 2 个
            .replace("\n{3,}", "\n\n")
            .strip()
        )
