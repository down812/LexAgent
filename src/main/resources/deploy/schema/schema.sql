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