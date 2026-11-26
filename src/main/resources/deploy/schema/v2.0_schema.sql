-- ============================================
-- LexAgent V2.0 增量数据库迁移脚本
-- 新增：案件管理 / 案件事实 / 案件会话关联 / 案件证据
-- 说明：仅在 V1.0 基础上增量建表，不修改已有表结构
-- 执行前提：已执行 schema.sql（lex_agent 库已存在）
-- ============================================

USE `lex_agent`;

-- ============================================
-- V2.0-1. 案件表 (cases)
-- ============================================
CREATE TABLE IF NOT EXISTS `cases` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '案件ID',
    `case_no`     varchar(64)           DEFAULT NULL COMMENT '案件编号(CASE+日期+ID)',
    `user_id`     bigint       NOT NULL COMMENT '所属用户ID',
    `case_name`   varchar(200) NOT NULL COMMENT '案件名称',
    `case_type`   varchar(50)  NOT NULL COMMENT '案件类型(劳动争议/合同纠纷/消费者权益纠纷等)',
    `description` text                  COMMENT '案件描述',
    `occurred_at` datetime              DEFAULT NULL COMMENT '案件发生时间',
    `location`    varchar(200)          DEFAULT NULL COMMENT '案件发生地点',
    `parties_involved` varchar(500)      DEFAULT NULL COMMENT '涉及主体（公司、个人等）',
    `status`      varchar(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '案件状态(ACTIVE进行中/PENDING待补充/COMPLETED已完成/ARCHIVED已归档)',
    `remark`      text                  COMMENT '备注',
    `created_at`  datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_no` (`case_no`),
    KEY `idx_case_user_id` (`user_id`),
    KEY `idx_case_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件表';

-- ============================================
-- V2.0-2. 案件事实表 (case_facts)
-- ============================================
CREATE TABLE IF NOT EXISTS `case_facts` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `case_id`      bigint        NOT NULL COMMENT '案件ID',
    `fact_content` text          NOT NULL COMMENT '事实内容',
    `fact_source`  varchar(30)   NOT NULL DEFAULT 'USER_INPUT' COMMENT '来源类型(USER_INPUT用户输入/OCR_EXTRACTION识别提取/AI_ANALYSIS分析提取/USER_CORRECTION用户修正)',
    `confidence`   decimal(5,2)           DEFAULT 100.00 COMMENT '信息置信度(0-100)',
    `confirmed`    tinyint                DEFAULT 0 COMMENT '是否经用户确认(0否/1是)',
    `created_at`   datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_case_facts_case_id` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件事实表';

-- ============================================
-- V2.0-3. 案件会话关联表 (case_conversations)
-- ============================================
CREATE TABLE IF NOT EXISTS `case_conversations` (
    `id`              bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `case_id`         bigint   NOT NULL COMMENT '案件ID',
    `conversation_id` bigint   NOT NULL COMMENT '会话主键ID(关联conversations.id)',
    `created_at`      datetime          DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_conversation` (`case_id`, `conversation_id`),
    KEY `idx_cc_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件会话关联表';

-- ============================================
-- V2.0-4. 案件证据表 (case_evidence)
-- ============================================
CREATE TABLE IF NOT EXISTS `case_evidence` (
    `id`              bigint   NOT NULL AUTO_INCREMENT COMMENT '证据ID',
    `case_id`         bigint   NOT NULL COMMENT '所属案件ID',
    `user_id`         bigint   NOT NULL COMMENT '上传用户ID',
    `evidence_no`     varchar(64)       DEFAULT NULL COMMENT '证据编号(EV+日期+ID)',
    `evidence_name`   varchar(200) NOT NULL COMMENT '证据名称',
    `original_name`   varchar(200)      DEFAULT NULL COMMENT '原始文件名称',
    `file_type`       varchar(30)  NOT NULL COMMENT '文件类型(PDF/DOC/DOCX/TXT/IMAGE)',
    `file_path`       varchar(500) NOT NULL COMMENT '文件存储路径',
    `category`        varchar(50)       DEFAULT '其他材料' COMMENT '证据分类(身份证明/合同协议/财务凭证/沟通记录/书面材料/影像资料/其他材料)',
    `ocr_text`        longtext          COMMENT 'OCR/解析识别文本',
    `corrected_text`  longtext          COMMENT '用户修正后文本',
    `ocr_status`      varchar(20)  NOT NULL DEFAULT 'WAITING' COMMENT 'OCR处理状态(WAITING等待/PROCESSING识别中/SUCCESS成功/FAILED失败)',
    `ocr_error_msg`   varchar(500)      DEFAULT NULL COMMENT 'OCR识别失败原因',
    `analysis_status` varchar(20)       DEFAULT NULL COMMENT '证据分析状态(阶段三：EVIDENCE_ANALYSIS)',
    `remark`          text              COMMENT '备注',
    `created_at`      datetime          DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `updated_at`      datetime          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_evidence_case_id` (`case_id`),
    KEY `idx_evidence_user_id` (`user_id`),
    KEY `idx_evidence_ocr_status` (`ocr_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案件证据表';

-- ============================================
-- V2.0-5. 证据分析结果表 (evidence_analysis)
-- 存储 Evidence Analysis Agent 的结构化分析结果
-- 一条证据可多次分析，查询时取最新一条
-- ============================================
CREATE TABLE IF NOT EXISTS `evidence_analysis` (
    `id`              bigint  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `evidence_id`     bigint  NOT NULL COMMENT '证据ID',
    `evidence_type`   varchar(100)     DEFAULT NULL COMMENT '证据类型(如：劳动合同/银行转账记录)',
    `extracted_data`  json             DEFAULT NULL COMMENT '提取的结构化信息(关键信息/不确定信息/分类推荐等)',
    `related_facts`   json             DEFAULT NULL COMMENT '关联案件事实(含关联依据与用户确认状态)',
    `analysis_result` text             DEFAULT NULL COMMENT '综合分析结论文本',
    `confidence`      decimal(5,2)     DEFAULT NULL COMMENT '分析置信度(0-100)',
    `created_at`      datetime         DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
    PRIMARY KEY (`id`),
    KEY `idx_ea_evidence_id` (`evidence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证据分析结果表';

-- ============================================
-- V2.0-6. 证据清单表 (evidence_checklist)
-- LIST-001~004：根据案件事实生成的个性化证据收集清单
-- ============================================
CREATE TABLE IF NOT EXISTS `evidence_checklist` (
    `id`            bigint   NOT NULL AUTO_INCREMENT COMMENT '清单项目ID',
    `case_id`       bigint   NOT NULL COMMENT '所属案件ID',
    `evidence_name` varchar(200) NOT NULL COMMENT '建议证据名称',
    `purpose`       varchar(500)      DEFAULT NULL COMMENT '证据用途',
    `status`        varchar(20)  NOT NULL DEFAULT 'REQUIRED' COMMENT '状态(REQUIRED建议收集/UPLOADED已上传/CONFIRMED用户已确认/OPTIONAL可选材料)',
    `related_fact`  varchar(500)      DEFAULT NULL COMMENT '关联案件事实',
    `remark`        varchar(500)      DEFAULT NULL COMMENT '备注',
    `created_at`    datetime          DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    datetime          DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_checklist_case_id` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证据清单表';

-- ============================================
-- V2.0-7. 证据清单关联表 (evidence_checklist_relation)
-- 清单项目与实际上传证据的关联（一对多）
-- ============================================
CREATE TABLE IF NOT EXISTS `evidence_checklist_relation` (
    `id`           bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `checklist_id` bigint   NOT NULL COMMENT '清单项目ID',
    `evidence_id`  bigint   NOT NULL COMMENT '证据ID',
    `created_at`   datetime          DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_checklist_evidence` (`checklist_id`, `evidence_id`),
    KEY `idx_ecr_evidence_id` (`evidence_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='证据清单关联表';

-- ============================================
-- V2.0-8. agent_tasks 扩展（12.9）
-- 增加 case_id（所属案件）与 task_group_id（一次完整协作任务标识）
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
--       使用 INFORMATION_SCHEMA + 动态 SQL 保证可重复执行
-- ============================================
DELIMITER $$
DROP PROCEDURE IF EXISTS `lexagent_v2_migrate_agent_tasks`$$
CREATE PROCEDURE `lexagent_v2_migrate_agent_tasks`()
BEGIN
    -- case_id
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_tasks' AND COLUMN_NAME = 'case_id'
    ) THEN
        ALTER TABLE `agent_tasks`
            ADD COLUMN `case_id` bigint DEFAULT NULL COMMENT '所属案件ID（V2.0）' AFTER `message_id`,
            ADD KEY `idx_agent_tasks_case_id` (`case_id`);
    END IF;
    -- task_group_id
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_tasks' AND COLUMN_NAME = 'task_group_id'
    ) THEN
        ALTER TABLE `agent_tasks`
            ADD COLUMN `task_group_id` varchar(64) DEFAULT NULL COMMENT '一次完整Agent协作任务标识(UUID)（V2.0）' AFTER `case_id`,
            ADD KEY `idx_agent_tasks_group_id` (`task_group_id`);
    END IF;
END$$
DELIMITER ;
CALL `lexagent_v2_migrate_agent_tasks`();
DROP PROCEDURE IF EXISTS `lexagent_v2_migrate_agent_tasks`;

-- ============================================
-- V2.0-9. 回答评价表 (answer_feedback)（12.8）
-- 用户对 AI 回答的评价反馈（帮助/没帮助 + 原因 + 文字反馈）
-- ============================================
CREATE TABLE IF NOT EXISTS `answer_feedback` (
    `id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`    bigint       NOT NULL COMMENT '评价用户ID',
    `message_id` bigint       NOT NULL COMMENT '回答消息ID（关联messages.id）',
    `rating`     tinyint      NOT NULL COMMENT '评价结果(1有帮助/0没有帮助)',
    `reason`     varchar(100)          DEFAULT NULL COMMENT '评价原因（没帮助时：法律依据不准确/内容不完整/表达不清晰/解决不了问题/其他）',
    `content`    text                  COMMENT '文字反馈',
    `created_at` datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    `updated_at` datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feedback_user_message` (`user_id`, `message_id`),
    KEY `idx_feedback_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回答评价表';
