package com.yk.demoai.service;

import com.yk.demoai.model.DatasourceDescriptor;

import java.util.List;
import java.util.Map;

/**
 * 负责只读 SQL 的安全校验与执行。
 */
public interface ReadOnlySqlExecutor {

    /**
     * 在目标数据源上执行只读查询。
     */
    List<Map<String, Object>> execute(DatasourceDescriptor datasource, String sql);

    /**
     * 判断 SQL 是否属于允许执行的只读语句。
     */
    boolean isReadOnly(String sql);
}
