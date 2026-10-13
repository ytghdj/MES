-- MES 产品资料。仅新增业务表和菜单，不清空或修改已有业务数据。
-- MySQL 8+；先在隔离库验证，业务库执行前备份。可重复执行。
CREATE TABLE IF NOT EXISTS mes_product (
    id BIGINT NOT NULL COMMENT '固定产品 ID',
    product_code VARCHAR(64) COLLATE utf8mb4_0900_as_ci NOT NULL COMMENT '产品编码，大小写不敏感',
    product_name VARCHAR(128) NOT NULL COMMENT '产品名称',
    specification VARCHAR(255) DEFAULT '' COMMENT '规格',
    material_grade VARCHAR(64) DEFAULT '' COMMENT '材料牌号',
    drawing_no VARCHAR(64) DEFAULT '' COMMENT '图号',
    customer_part_no VARCHAR(64) DEFAULT '' COMMENT '客户料号',
    status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    attachment_ids VARCHAR(128) DEFAULT '' COMMENT '逗号分隔 OSS ID，最多5个',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_product_code (product_code),
    KEY idx_mes_product_status_created (del_flag, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci COMMENT='MES 产品资料';

START TRANSACTION;
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, is_frame, is_cache, create_time)
SELECT 1791900000000010000, '基础数据管理', 0, 5, 'mes', NULL, 'M', '0', '0', '', 'tree', 'N', 'Y', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1791900000000010000);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, perms, icon, is_frame, is_cache, create_time)
SELECT 1791900000000010100, '产品资料', 1791900000000010000, 1, 'product', 'mes/product/index', 'C', '0', '0', 'mes:product:list', 'documentation', 'N', 'Y', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 1791900000000010100);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, menu_type, visible, status, perms, icon, is_frame, is_cache, create_time)
SELECT buttons.id, buttons.name, 1791900000000010100, buttons.sort, '#', 'F', '0', '0', buttons.perms, '#', 'N', 'Y', NOW()
FROM (
    SELECT 1791900000000010101 id, '产品查询' name, 1 sort, 'mes:product:query' perms
    UNION ALL SELECT 1791900000000010102, '产品新增', 2, 'mes:product:add'
    UNION ALL SELECT 1791900000000010103, '产品编辑及启停', 3, 'mes:product:edit'
    UNION ALL SELECT 1791900000000010104, '产品删除', 4, 'mes:product:remove'
    UNION ALL SELECT 1791900000000010105, '产品导入', 5, 'mes:product:import'
    UNION ALL SELECT 1791900000000010106, '产品导出', 6, 'mes:product:export'
    UNION ALL SELECT 1791900000000010107, '产品附件上传', 7, 'mes:product:attachmentUpload'
    UNION ALL SELECT 1791900000000010108, '产品附件预览', 8, 'mes:product:attachmentPreview'
    UNION ALL SELECT 1791900000000010109, '产品附件下载', 9, 'mes:product:attachmentDownload'
) buttons
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.menu_id = buttons.id);

-- 仅为现有超级管理员角色分配；其他角色由管理员明确授权。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT roles.role_id, menus.menu_id FROM sys_role roles JOIN sys_menu menus
    ON menus.menu_id IN (1791900000000010000, 1791900000000010100,
        1791900000000010101,1791900000000010102,1791900000000010103,1791900000000010104,
        1791900000000010105,1791900000000010106,1791900000000010107,1791900000000010108,1791900000000010109)
WHERE roles.role_key = 'superadmin' AND roles.del_flag = '0' AND roles.status = '0'
    AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = roles.role_id AND existing.menu_id = menus.menu_id);
COMMIT;
