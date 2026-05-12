USE loan_business_db;

-- ==============================================
-- 逻辑外键关系初始化 (FOREIGN_KEY)
-- 注意：这些关系会指导 LLM 在生成 SQL 时进行 JOIN，并用关联表的可读字段替代 ID 字段
-- ==============================================

-- 1. 贷款合同 → 客户 (1:N) - 查询贷款合同时显示客户姓名而非客户ID
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_type, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'loan_contract', 'cust_id', 'customer', 'cust_id', '1:N', 'FOREIGN_KEY', '贷款合同通过客户ID关联客户主表，查询时应显示customer.cust_name，不要显示loan_contract.cust_id');

-- 2. 还款计划 → 贷款合同 (1:N)
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_type, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'repayment_plan', 'contract_id', 'loan_contract', 'contract_id', '1:N', 'FOREIGN_KEY', '还款计划通过合同ID关联贷款合同');

-- 3. 还款流水 → 贷款合同 (1:N)
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_type, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'repayment_record', 'contract_id', 'loan_contract', 'contract_id', '1:N', 'FOREIGN_KEY', '还款流水通过合同ID关联贷款合同');

-- 4. 还款流水 → 还款计划 (1:N)
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_type, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'repayment_record', 'plan_id', 'repayment_plan', 'plan_id', '1:N', 'FOREIGN_KEY', '还款流水通过计划ID关联还款计划');

-- 5. 逾期记录 → 贷款合同 (1:N)
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_type, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'overdue_record', 'contract_id', 'loan_contract', 'contract_id', '1:N', 'FOREIGN_KEY', '逾期记录通过合同ID关联贷款合同');

-- 6. 风控评分 → 客户 (1:1) - 查询风控评分时显示客户姓名而非客户ID
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_type, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'credit_risk', 'cust_id', 'customer', 'cust_id', '1:1', 'FOREIGN_KEY', '风控评分通过客户ID关联客户主表，查询时应显示customer.cust_name，不要显示credit_risk.cust_id');

-- ==============================================
-- 字典映射关系初始化 (DICT_MAPPING)
-- 将编码字段转换为可读的中文名称
-- ==============================================

-- 7. 贷款用途字段 → 业务字典
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'loan_contract', 'loan_purpose', 'dict_biz', 'dict_code', 'DICT_MAPPING', 'dict_type=loan_purpose');

-- 8. 贷款状态字段 → 业务字典
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'loan_contract', 'loan_status', 'dict_biz', 'dict_code', 'DICT_MAPPING', 'dict_type=loan_status');

-- 9. 还款状态字段 → 业务字典（需要先在dict_biz表中插入相关数据）
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'repayment_record', 'pay_status', 'dict_biz', 'dict_code', 'DICT_MAPPING', 'dict_type=pay_status');

-- 10. 风险标签字段 → 业务字典（需要先在dict_biz表中插入相关数据）
INSERT INTO logical_relation (
    datasource_id, 
    source_table_name, 
    source_column_name, 
    target_table_name, 
    target_column_name, 
    relation_category, 
    description
) VALUES 
('loan_business_db', 'credit_risk', 'risk_tag', 'dict_biz', 'dict_code', 'DICT_MAPPING', 'dict_type=risk_tag');

-- ==============================================
-- 补充字典数据
-- ==============================================
INSERT IGNORE INTO dict_biz(dict_type,dict_code,dict_name) VALUES
    ('pay_status','1','正常还款'),
    ('pay_status','2','提前还款'),
    ('pay_status','3','逾期还款'),
    ('risk_tag','1','低风险'),
    ('risk_tag','2','中风险'),
    ('risk_tag','3','高风险');

-- ==============================================
-- 查询验证
-- ==============================================
SELECT id, source_table_name, source_column_name, target_table_name, target_column_name, relation_category, description 
FROM logical_relation 
ORDER BY relation_category, id;
