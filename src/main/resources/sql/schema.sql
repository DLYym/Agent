CREATE TABLE IF NOT EXISTS logical_relation (
    id INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    datasource_id VARCHAR(100) NOT NULL COMMENT '关联的数据源ID (对应 DatasourceDescriptor.id)',
    source_table_name VARCHAR(100) NOT NULL COMMENT '主表名 (例如 t_order)',
    source_column_name VARCHAR(100) NOT NULL COMMENT '主表字段名 (例如 buyer_uid)',
    target_table_name VARCHAR(100) NOT NULL COMMENT '关联表名 (例如 t_user)',
    target_column_name VARCHAR(100) NOT NULL COMMENT '关联表字段名 (例如 id)',
    relation_type VARCHAR(20) DEFAULT NULL COMMENT '关系类型: 1:1, 1:N, N:1 (辅助LLM理解数据基数，可选)',
    description VARCHAR(500) DEFAULT NULL COMMENT '业务描述: 存入Prompt中帮助LLM理解 (例如: 订单表通过buyer_uid关联用户表id)',
    is_deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_datasource_id (datasource_id) COMMENT '加速根据数据源查找关系的查询',
    INDEX idx_source_table (datasource_id, source_table_name) COMMENT '加速根据表名查找关系的查询'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '逻辑外键配置表';
