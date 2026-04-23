CREATE DATABASE IF NOT EXISTS loan_business_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE loan_business_db;


-- 1. 客户信息表
CREATE TABLE customer (
                          cust_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '客户ID',
                          cust_name VARCHAR(20) NOT NULL COMMENT '姓名',
                          id_card VARCHAR(18) UNIQUE COMMENT '身份证号',
                          phone VARCHAR(11) COMMENT '手机号',
                          age INT COMMENT '年龄',
                          gender CHAR(2) COMMENT '性别',
                          marriage VARCHAR(10) COMMENT '婚姻状态',
                          occupation VARCHAR(30) COMMENT '职业',
                          income DECIMAL(12,2) COMMENT '月收入',
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '客户主表';

-- 2. 贷款合同表
CREATE TABLE loan_contract (
                               contract_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '合同编号',
                               cust_id INT NOT NULL COMMENT '客户ID',
                               loan_amount DECIMAL(12,2) NOT NULL COMMENT '贷款金额',
                               loan_term INT COMMENT '贷款期限(月)',
                               interest_rate DECIMAL(5,2) COMMENT '年化利率%',
                               loan_purpose VARCHAR(50) COMMENT '贷款用途',
                               apply_time DATETIME COMMENT '申请时间',
                               approve_time DATETIME COMMENT '审批时间',
                               loan_status TINYINT COMMENT '状态 1待审批 2放款中 3正常还款 4结清 5坏账',
                               FOREIGN KEY (cust_id) REFERENCES customer(cust_id)
) COMMENT '贷款合同表';

-- 3. 还款计划表
CREATE TABLE repayment_plan (
                                plan_id INT PRIMARY KEY AUTO_INCREMENT,
                                contract_id INT NOT NULL,
                                period INT COMMENT '第几期',
                                total_amount DECIMAL(12,2) COMMENT '当期应还总额',
                                principal DECIMAL(12,2) COMMENT '本金',
                                interest DECIMAL(12,2) COMMENT '利息',
                                due_date DATE COMMENT '还款日',
                                FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) COMMENT '分期还款计划';

-- 4. 真实还款流水表
CREATE TABLE repayment_record (
                                  record_id INT PRIMARY KEY AUTO_INCREMENT,
                                  contract_id INT NOT NULL,
                                  plan_id INT NOT NULL,
                                  pay_amount DECIMAL(12,2) COMMENT '实际还款金额',
                                  pay_time DATETIME COMMENT '还款时间',
                                  pay_status TINYINT COMMENT '1正常 2提前还 3逾期还',
                                  FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) COMMENT '还款流水';

-- 5. 逾期记录表
CREATE TABLE overdue_record (
                                overdue_id INT PRIMARY KEY AUTO_INCREMENT,
                                contract_id INT NOT NULL,
                                overdue_days INT COMMENT '逾期天数',
                                overdue_fee DECIMAL(12,2) COMMENT '罚息',
                                overdue_level VARCHAR(10) COMMENT '逾期等级 M1 M2 M3',
                                FOREIGN KEY (contract_id) REFERENCES loan_contract(contract_id)
) COMMENT '逾期台账';

-- 6. 风控信用评分表
CREATE TABLE credit_risk (
                             risk_id INT PRIMARY KEY AUTO_INCREMENT,
                             cust_id INT UNIQUE NOT NULL,
                             credit_score INT COMMENT '信用评分 0-100',
                             credit_level CHAR(3) COMMENT '信用等级 A/B/C/D',
                             risk_tag TINYINT COMMENT '风险标签 1低风险 2中风险 3高风险',
                             FOREIGN KEY (cust_id) REFERENCES customer(cust_id)
) COMMENT '风控评分表';

-- 7. 业务数据字典表
CREATE TABLE dict_biz (
                          dict_id INT PRIMARY KEY AUTO_INCREMENT,
                          dict_type VARCHAR(30) COMMENT '字典类型',
                          dict_code VARCHAR(20) COMMENT '字典编码',
                          dict_name VARCHAR(50) COMMENT '字典名称'
) COMMENT '业务字典';



-- 插入客户数据
INSERT INTO customer(cust_name,id_card,phone,age,gender,marriage,occupation,income) VALUES
                                                                                        ('张三','3201021998xxxx1234','13800138000',26,'男','未婚','互联网职员',9500.00),
                                                                                        ('李四','3201021995xxxx2345','13900139000',31,'男','已婚','企业员工',12000.00),
                                                                                        ('王五','3201021993xxxx3456','13700137000',33,'女','已婚','事业单位',8500.00),
                                                                                        ('赵六','3201021999xxxx4567','13600136000',25,'男','未婚','自由职业',7000.00),
                                                                                        ('钱七','3201021990xxxx5678','13500135000',34,'女','离异','技术岗',15000.00);

-- 插入贷款合同
INSERT INTO loan_contract(cust_id,loan_amount,loan_term,interest_rate,loan_purpose,apply_time,approve_time,loan_status) VALUES
                                                                                                                            (1,50000.00,12,6.8,'日常消费','2025-01-05','2025-01-06',3),
                                                                                                                            (2,120000.00,24,5.9,'购车贷款','2025-02-10','2025-02-11',3),
                                                                                                                            (3,30000.00,6,7.2,'装修贷','2025-03-15','2025-03-16',2),
                                                                                                                            (4,20000.00,12,8.5,'个人周转','2025-04-01','2025-04-02',5),
                                                                                                                            (5,80000.00,18,6.3,'经营贷','2025-05-08','2025-05-09',3);

-- 还款计划
INSERT INTO repayment_plan(contract_id,period,total_amount,principal,interest,due_date) VALUES
                                                                                            (1,1,4320.56,4000.00,320.56,'2025-02-06'),
                                                                                            (1,2,4320.56,4022.67,297.89,'2025-03-06'),
                                                                                            (2,1,5365.89,5000.00,365.89,'2025-03-11');

-- 还款流水
INSERT INTO repayment_record(contract_id,plan_id,pay_amount,pay_time,pay_status) VALUES
                                                                                     (1,1,4320.56,'2025-02-05',1),
                                                                                     (2,2,5365.89,'2025-03-10',1);

-- 逾期记录
INSERT INTO overdue_record(contract_id,overdue_days,overdue_fee,overdue_level) VALUES
    (4,28,320.50,'M1');

-- 风控评分
INSERT INTO credit_risk(cust_id,credit_score,credit_level,risk_tag) VALUES
                                                                        (1,85,'A',1),
                                                                        (2,79,'B',1),
                                                                        (3,72,'B',2),
                                                                        (4,51,'D',3),
                                                                        (5,88,'A',1);

-- 业务字典
INSERT INTO dict_biz(dict_type,dict_code,dict_name) VALUES
                                                        ('loan_purpose','1','消费贷'),
                                                        ('loan_purpose','2','购车贷'),
                                                        ('loan_purpose','3','装修贷'),
                                                        ('loan_status','1','待审批'),
                                                        ('loan_status','2','已放款'),
                                                        ('loan_status','3','正常还款'),
                                                        ('loan_status','4','已结清'),
                                                        ('loan_status','5','坏账逾期');