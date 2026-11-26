package org.lexagent.controller.chat;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.lexagent.context.LoginContext;
import org.lexagent.exception.BusinessException;
import org.lexagent.model.dto.ChatRequestDTO;
import org.lexagent.model.entity.Conversation;
import org.lexagent.model.entity.Message;
import org.lexagent.model.vo.AgentEvent;
import org.lexagent.model.vo.ChatMessageVO;
import org.lexagent.model.vo.ConversationVO;
import org.lexagent.model.vo.FinalLegalAnswer;
import org.lexagent.model.vo.OcrResultVO;
import org.lexagent.result.Result;
import org.lexagent.service.ConversationService;
import org.lexagent.service.MessageService;
import org.lexagent.service.agent.SupervisorAgentService;
import org.lexagent.utils.OcrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 法律智能问诊控制器（SSE 流式输出）
 * <p>
 * 对应需求文档第4章（法律智能问诊）+ 第11章（SSE 流式输出）。
 * 用户提交法律问题 → 多 Agent 协作 → SSE 实时推送处理过程 → 最终回答。
 */
@Tag(name = "法律智能问诊")
@RestController
@Slf4j
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private SupervisorAgentService supervisorAgentService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private org.lexagent.service.CaseContextService caseContextService;

    @Autowired
    private org.lexagent.service.CaseService caseService;

    @Autowired
    private AsyncTaskExecutor asyncTaskExecutor;

    @Autowired
    private OcrClient ocrClient;

    @Value("${lexagent.rag.agent.timeout-ms:120000}")
    private long agentTimeoutMs;

    /**
     * 法律问诊（SSE 流式）
     * <p>
     * 前端使用 fetch() 消费 text/event-stream，响应头需携带 token。
     * 事件序列：agent_start → agent_result → citation → content → done
     * <p>
     * V2.0（CHAT-001/003）：可选传入 caseId ——
     *   1. 同步完成案件权限校验与上下文组装（异步线程无登录态）；
     *   2. 会话与案件建立关联（案件详情可查问诊记录）；
     *   3. Supervisor 按请求类型决定是否插入 Evidence Analysis Agent。
     * 未传 caseId 时完全保持 V1.0 流程。
     */
    @PostMapping(value = "/consult", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "法律智能问诊（SSE流式输出）",
            description = "用户提交法律问题，系统通过多Agent协作生成法律回答，SSE实时推送处理过程；可传 caseId 注入案件上下文")
    public SseEmitter consult(@Valid @RequestBody ChatRequestDTO dto) {
        // 1. 同步获取登录用户ID（异步后 ThreadLocal 不可用）
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }

        // 2. 案件上下文（V2.0）：权限校验 + 组装必须在主线程完成
        org.lexagent.model.vo.CaseContextVO loadedContext = null;
        if (dto.getCaseId() != null) {
            loadedContext = caseContextService.load(dto.getCaseId()); // 内部含属主/管理员校验
        }
        final org.lexagent.model.vo.CaseContextVO caseContext = loadedContext;

        log.info("法律问诊请求：userId={}, conversationId={}, query长度={}, caseId={}",
                userId, dto.getConversationId(), StrUtil.length(dto.getQuery()), dto.getCaseId());

        // 3. 创建/获取会话（conversationId 为前端生成的业务UUID，getOrCreate 后拿到自增主键 id）
        Conversation conversation = conversationService.getOrCreate(
                userId, dto.getConversationId(), dto.getQuery());
        // 后续关联 messages/agent_tasks 均使用 conversations.id（自增主键）
        final Long conversationId = conversation.getId();

        // 4. 会话与案件关联（CHAT-003：每次问诊明确所属案件；已关联则跳过）
        if (caseContext != null) {
            caseService.linkConversationIfAbsent(caseContext.getCaseId(), conversationId);
        }

        // 5. 保存用户消息
        Message userMessage = messageService.save(conversationId,
                "user", dto.getQuery());
        final Long messageId = userMessage.getId();

        // 6. 创建 SseEmitter，超时设为 Agent 超时 + 缓冲
        SseEmitter emitter = new SseEmitter(agentTimeoutMs + 30000);

        // 7. 异步执行多 Agent 编排（案件上下文已提前组装，异步线程直接使用）
        asyncTaskExecutor.execute(() -> {
            try {
                FinalLegalAnswer answer = supervisorAgentService.execute(
                        dto.getQuery(), conversationId, messageId,
                        caseContext, event -> sendSseEvent(emitter, event));

                // 8. 保存 assistant 消息（最终回答的 JSON）
                String answerJson = JSONUtil.toJsonStr(answer);
                Message answerMessage = messageService.save(conversationId, "assistant", answerJson);

                // 9. done 事件携带真实消息ID（V2.0 11.6：前端用于回答评价入口）
                sendSseEvent(emitter, AgentEvent.of(
                        "done", null, null,
                        java.util.Map.of("assistantMessageId", answerMessage.getId()),
                        "处理完成"));

            } catch (Exception e) {
                log.error("多Agent编排失败：{}", e.getMessage(), e);
                sendSseEvent(emitter, AgentEvent.of(
                        "error", null, null, null,
                        "处理失败：" + e.getMessage()));
            } finally {
                emitter.complete();
            }
        });

        // 超时和错误处理
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            emitter.complete();
        });
        emitter.onError(e -> {
            log.warn("SSE 连接异常：{}", e.getMessage());
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 发送 SSE 事件
     */
    private void sendSseEvent(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getEvent())
                    .data(event)
                    .id(String.valueOf(event.getTimestamp())));
        } catch (IOException e) {
            log.warn("SSE 发送失败：{}", e.getMessage());
        }
    }

    // ==================== 图片/PDF 提问（OCR 识别） ====================

    /**
     * 上传图片或 PDF 进行 OCR 识别，返回识别文字
     * <p>
     * 用户上传法律文书/合同/证据照片 → OCR 提取文字 → 前端展示供审阅 →
     * 用户确认后以识别文字（或拼接自身描述）作为 query 调用 /chat/consult 走 SSE 问诊。
     * <p>
     * 支持多文件同时上传，分别返回每个文件的识别结果。
     */
    @PostMapping("/ocr")
    @Operation(summary = "图片/PDF OCR 识别", description = "上传图片或扫描PDF，返回识别出的文字内容，供用户审阅后提交问诊")
    public Result<List<OcrResultVO>> ocr(@RequestParam("files") MultipartFile[] files) {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        if (files == null || files.length == 0) {
            return Result.error("请至少上传一个文件");
        }

        // 检查 OCR 服务是否可用
        if (!ocrClient.isEnabled()) {
            return Result.error("OCR 功能未启用（lexagent.rag.ocr.enabled=false）");
        }
        if (!ocrClient.isAvailable()) {
            return Result.error("OCR 服务不可用，请先启动 ocr-service（见 ocr-service/README.md）");
        }

        log.info("OCR 识别请求：userId={}, 文件数={}", userId, files.length);
        List<OcrResultVO> results = new ArrayList<>();

        for (MultipartFile file : files) {
            OcrResultVO vo = processOcrFile(file);
            results.add(vo);
        }

        return Result.success("OCR 识别完成", results);
    }

    /**
     * 处理单个文件的 OCR 识别
     */
    private OcrResultVO processOcrFile(MultipartFile file) {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        String ext = getExtension(originalName).toLowerCase();

        OcrResultVO.OcrResultVOBuilder builder = OcrResultVO.builder()
                .fileName(originalName);

        // 判断文件类型
        boolean isPdf = "pdf".equals(ext);
        boolean isImage = "jpg".equals(ext) || "jpeg".equals(ext)
                || "png".equals(ext) || "bmp".equals(ext) || "webp".equals(ext);
        if (!isPdf && !isImage) {
            return builder.fileType("UNSUPPORTED")
                    .success(false)
                    .errorMsg("不支持的文件类型，仅支持图片(JPG/PNG/BMP/WEBP)和PDF")
                    .build();
        }

        builder.fileType(isPdf ? "PDF" : "IMAGE");

        // 保存到临时文件 → OCR → 删除
        Path tempFile = null;
        try {
            String suffix = isPdf ? ".pdf" : "." + ext;
            tempFile = Files.createTempFile("lexagent-ocr-", suffix);
            file.transferTo(tempFile.toFile());

            String text;
            if (isPdf) {
                text = ocrClient.recognizePdf(tempFile.toAbsolutePath().toString());
            } else {
                text = ocrClient.recognizeImage(tempFile.toAbsolutePath().toString());
            }

            text = StrUtil.blankToDefault(text, "");
            return builder
                    .text(text)
                    .charCount(text.length())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("OCR 识别失败：{} - {}", originalName, e.getMessage());
            return builder
                    .success(false)
                    .errorMsg("OCR 识别失败：" + e.getMessage())
                    .build();
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile.toFile());
            }
        }
    }

    private String getExtension(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx < 0 || idx == name.length() - 1 ? "" : name.substring(idx + 1);
    }

    // ==================== 会话与历史消息管理 ====================

    @GetMapping("/conversations")
    @Operation(summary = "查询当前用户会话列表")
    public Result<List<ConversationVO>> listConversations() {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        return Result.success(conversationService.listByUser(userId));
    }

    @GetMapping("/messages/{conversationId}")
    @Operation(summary = "查询会话历史消息")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable String conversationId) {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        // 前端传入的是业务UUID，先校验归属并拿到自增主键 id，再用 id 查历史消息
        Conversation conversation = conversationService.getByIdAndUser(conversationId, userId);
        return Result.success(messageService.listByConversation(conversation.getId()));
    }

    @DeleteMapping("/conversation/{conversationId}")
    @Operation(summary = "删除会话（归档）")
    public Result<String> deleteConversation(@PathVariable String conversationId) {
        Long userId = LoginContext.getUserId();
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        conversationService.archive(conversationId, userId);
        return Result.success("会话已删除");
    }
}
