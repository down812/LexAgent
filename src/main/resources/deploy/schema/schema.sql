-- ============================================
-- 多Agent法律援助系统 - 完整数据库表结构
-- 数据库名: lex_agent
-- 创建时间: 2026年
-- ============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `lex_agent`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;

USE `lex_agent`;

-- ============================================
-- 1. 用户表 (users)
-- ============================================
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                         `identifier` varchar(100) NOT NULL COMMENT '用户账号或者临时标识',
                         `password` varchar(128) DEFAULT NULL COMMENT '用户密码',
                         `phone` varchar(11) DEFAULT NULL COMMENT '用户联系电话',
                         `type` int NOT NULL COMMENT '用户类型（0-超级管理员，1-普通管理员，2-普通用户）',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '用户创建时间',
                         `updated_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '用户修改时间',
                         `last_active` datetime DEFAULT NULL COMMENT '上次活跃的时间',
                         `email` varchar(50) DEFAULT NULL,
                         PRIMARY KEY (`id`),
                         KEY `identifier_index` (`identifier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ============================================
-- 2. 知识库分类表 (dataset)
--    管理不同类型法律知识（劳动法/消费者权益/民事纠纷 等）
-- ============================================
DROP TABLE IF EXISTS `dataset`;
CREATE TABLE `dataset` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
                         `name` varchar(100) NOT NULL COMMENT '知识库名称',
                         `domain` varchar(50) NOT NULL COMMENT '法律领域(劳动法/消费者权益/民事纠纷)',
                         `description` varchar(500) DEFAULT NULL COMMENT '描述',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         KEY `idx_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律知识库分类表';

-- ============================================
-- 3. 知识库文件记录表 (dataset_file)
--    记录上传的法律文件及其解析/向量化状态
-- ============================================
DROP TABLE IF EXISTS `dataset_file`;
CREATE TABLE `dataset_file` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
                         `dataset_id` bigint NOT NULL COMMENT '所属知识库ID',
                         `file_name` varchar(200) NOT NULL COMMENT '文件名',
                         `file_type` varchar(20) NOT NULL COMMENT '文件类型(PDF/DOCX/TXT/MD/IMAGE)',
                         `file_path` varchar(500) NOT NULL COMMENT '存储路径',
                         `status` varchar(20) NOT NULL DEFAULT 'WAITING' COMMENT '处理状态(WAITING/PROCESSING/SUCCESS/FAILED)',
                         `chunk_count` int DEFAULT 0 COMMENT '切分出的Chunk数量',
                         `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因',
                         `upload_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                         `process_time` datetime DEFAULT NULL COMMENT '处理完成时间',
                         PRIMARY KEY (`id`),
                         KEY `idx_dataset_id` (`dataset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库文件记录表';

-- ============================================
-- 4. 法律信息表 (legal_laws)
--    法律级元信息（向量数据存于 Elasticsearch，此表做结构化与校验）
-- ============================================
DROP TABLE IF EXISTS `legal_laws`;
CREATE TABLE `legal_laws` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '法律ID',
                         `law_name` varchar(200) NOT NULL COMMENT '法律名称',
                         `domain` varchar(50) NOT NULL COMMENT '法律领域',
                         `status` varchar(20) NOT NULL DEFAULT '有效' COMMENT '生效状态(有效/废止/已修订)',
                         `dataset_id` bigint DEFAULT NULL COMMENT '来源知识库ID',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_law_name` (`law_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律信息表';

-- ============================================
-- 5. 法律条款表 (legal_articles)
--    结构化的法律条款（按条款切分，与 ES 向量一一对应便于校验）
-- ============================================
DROP TABLE IF EXISTS `legal_articles`;
CREATE TABLE `legal_articles` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '条款ID',
                         `law_id` bigint NOT NULL COMMENT '所属法律ID',
                         `article_no` varchar(50) NOT NULL COMMENT '条款编号(第四十条)',
                         `chapter` varchar(100) DEFAULT NULL COMMENT '所属章节',
                         `content` text NOT NULL COMMENT '条款内容',
                         `dataset_file_id` bigint DEFAULT NULL COMMENT '来源文件ID',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         PRIMARY KEY (`id`),
                         KEY `idx_law_id` (`law_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律条款表';

-- ============================================
-- 6. 聊天会话表 (conversations)
--    用户每次法律问诊创建一个会话，关联消息记录
-- ============================================
DROP TABLE IF EXISTS `conversations`;
CREATE TABLE `conversations` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
                         `user_id` bigint NOT NULL COMMENT '用户ID',
                         `title` varchar(200) DEFAULT NULL COMMENT '会话标题（取首条消息摘要）',
                         `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE/ARCHIVED)',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天会话表';

-- ============================================
-- 7. 聊天消息表 (messages)
--    存储用户提问和系统回答（含多 Agent 中间结果）
-- ============================================
DROP TABLE IF EXISTS `messages`;
CREATE TABLE `messages` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
                         `conversation_id` bigint NOT NULL COMMENT '会话ID',
                         `role` varchar(20) NOT NULL COMMENT '角色(user/assistant)',
                         `content` text NOT NULL COMMENT '消息内容',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         PRIMARY KEY (`id`),
                         KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';

-- ============================================
-- 8. Agent执行记录表 (agent_tasks)
--    记录多 Agent 协作过程中每个 Agent 的输入/输出/状态
-- ============================================
DROP TABLE IF EXISTS `agent_tasks`;
CREATE TABLE `agent_tasks` (
                         `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
                         `conversation_id` bigint DEFAULT NULL COMMENT '关联会话ID',
                         `message_id` bigint DEFAULT NULL COMMENT '关联消息ID',
                         `agent_type` varchar(50) NOT NULL COMMENT 'Agent类型(CASE_ANALYSIS/LEGAL_SEARCH/LEGAL_ANALYSIS/REVIEWER)',
                         `input` text COMMENT '输入内容(JSON)',
                         `output` text COMMENT '输出内容(JSON)',
                         `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/RUNNING/SUCCESS/FAILED)',
                         `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时(毫秒)',
                         `error_msg` varchar(1000) DEFAULT NULL COMMENT '失败原因',
                         `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         PRIMARY KEY (`id`),
                         KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent执行记录表';