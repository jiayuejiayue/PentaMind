-- ============================================================
-- PentaMind Platform - 数据库初始化 SQL
-- MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS `pentamind` 
  DEFAULT CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE `pentamind`;

-- 扫描目标表
CREATE TABLE IF NOT EXISTS `pm_target` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL COMMENT '目标名称',
  `url` VARCHAR(500) NOT NULL COMMENT '目标URL',
  `type` VARCHAR(20) DEFAULT 'WEB' COMMENT '类型: WEB/API/APP',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0-待测试 1-测试中 2-已完成',
  `risk_level` VARCHAR(20) DEFAULT 'INFO' COMMENT '风险等级',
  `vuln_count` INT DEFAULT 0 COMMENT '漏洞数量',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB COMMENT='扫描目标';

-- 扫描任务表
CREATE TABLE IF NOT EXISTS `pm_scan_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_id` BIGINT DEFAULT NULL COMMENT '关联目标ID',
  `name` VARCHAR(200) NOT NULL COMMENT '任务名称',
  `scan_type` VARCHAR(30) NOT NULL COMMENT '扫描类型',
  `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
  `progress` INT DEFAULT 0 COMMENT '进度 0-100',
  `result` LONGTEXT DEFAULT NULL COMMENT '扫描结果JSON',
  `finding_count` INT DEFAULT 0 COMMENT '发现数量',
  `start_time` DATETIME DEFAULT NULL,
  `end_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_target_id` (`target_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_scan_type` (`scan_type`)
) ENGINE=InnoDB COMMENT='扫描任务';

-- 漏洞记录表
CREATE TABLE IF NOT EXISTS `pm_vulnerability` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_id` BIGINT DEFAULT NULL COMMENT '关联目标',
  `task_id` BIGINT DEFAULT NULL COMMENT '关联任务',
  `name` VARCHAR(200) NOT NULL COMMENT '漏洞名称',
  `type` VARCHAR(50) NOT NULL COMMENT '漏洞类型: XSS/SQLI/SSRF/...',
  `severity` VARCHAR(20) NOT NULL COMMENT '严重等级: CRITICAL/HIGH/MEDIUM/LOW',
  `url` VARCHAR(500) DEFAULT NULL COMMENT '漏洞URL',
  `parameter` VARCHAR(200) DEFAULT NULL COMMENT '漏洞参数',
  `payload` TEXT DEFAULT NULL COMMENT '验证Payload',
  `evidence` TEXT DEFAULT NULL COMMENT '证据',
  `description` TEXT DEFAULT NULL COMMENT '描述',
  `fix_suggestion` TEXT DEFAULT NULL COMMENT '修复建议',
  `status` VARCHAR(20) DEFAULT 'OPEN' COMMENT 'OPEN/CONFIRMED/FIXED/FALSE_POSITIVE',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_target_id` (`target_id`),
  INDEX `idx_severity` (`severity`),
  INDEX `idx_type` (`type`)
) ENGINE=InnoDB COMMENT='漏洞记录';

-- 插件状态表
CREATE TABLE IF NOT EXISTS `pm_plugin` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '插件名称',
  `version` VARCHAR(20) DEFAULT NULL COMMENT '版本',
  `type` VARCHAR(30) DEFAULT NULL COMMENT '类型: EXTENSION/PATHFINDER/...',
  `status` VARCHAR(20) DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
  `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最后心跳时间',
  `config` TEXT DEFAULT NULL COMMENT '插件配置JSON',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='插件管理';

-- ================= 新增侦察资产表 =================

-- 子域名表
CREATE TABLE IF NOT EXISTS `pm_subdomain` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_id` BIGINT NOT NULL COMMENT '所属目标ID',
  `domain` VARCHAR(255) NOT NULL COMMENT '子域名',
  `ip_list` VARCHAR(500) DEFAULT NULL COMMENT '解析IP(逗号分隔)',
  `status_code` INT DEFAULT NULL COMMENT 'HTTP状态码',
  `title` VARCHAR(255) DEFAULT NULL COMMENT '页面标题',
  `source` VARCHAR(50) DEFAULT 'Manual' COMMENT '发现来源: Subfinder/Amass/...',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_target` (`target_id`)
) ENGINE=InnoDB COMMENT='子域名资产';

-- 端口服务表
CREATE TABLE IF NOT EXISTS `pm_port` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_id` BIGINT NOT NULL COMMENT '所属目标ID',
  `ip` VARCHAR(50) NOT NULL COMMENT '主机IP',
  `port` INT NOT NULL COMMENT '端口号',
  `protocol` VARCHAR(20) DEFAULT 'tcp' COMMENT '协议: tcp/udp',
  `service` VARCHAR(100) DEFAULT NULL COMMENT '服务指纹: http/ssh/mysql',
  `version` VARCHAR(100) DEFAULT NULL COMMENT '服务版本',
  `state` VARCHAR(20) DEFAULT 'open' COMMENT '端口状态: open/filtered/...',
  `source` VARCHAR(50) DEFAULT 'Manual' COMMENT '发现来源: Nmap/Masscan/...',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_target` (`target_id`)
) ENGINE=InnoDB COMMENT='端口服务资产';

-- 敏感路径与目录表
CREATE TABLE IF NOT EXISTS `pm_path` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target_id` BIGINT NOT NULL COMMENT '所属目标ID',
  `url` VARCHAR(500) NOT NULL COMMENT '完整URL路径',
  `status_code` INT DEFAULT NULL COMMENT '响应状态码',
  `content_length` INT DEFAULT NULL COMMENT '返回包长度',
  `content_type` VARCHAR(100) DEFAULT NULL COMMENT '响应类型',
  `title` VARCHAR(255) DEFAULT NULL COMMENT '页面标题',
  `source` VARCHAR(50) DEFAULT 'Manual' COMMENT '发现来源: Dirsearch/Ffuf/...',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_target` (`target_id`)
) ENGINE=InnoDB COMMENT='Web路径资产';
