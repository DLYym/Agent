package com.yk.demoai.configure;

import com.yk.demoai.exception.AgentWorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 静态资源缺失直接返回 404，避免把浏览器探测请求打成大段异常堆栈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "资源不存在: " + e.getResourcePath()));
    }

    /**
     * Agent 工作流失败时，保留步骤轨迹返回给前端，便于定位是生成、校验还是执行阶段出错。
     */
    @ExceptionHandler(AgentWorkflowException.class)
    public ResponseEntity<Map<String, Object>> handleAgentWorkflowException(AgentWorkflowException e) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        body.put("steps", e.getSteps());
        if (StringUtils.hasText(e.getLastSql())) {
            body.put("sql", e.getLastSql());
        }
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        // 打印堆栈信息到控制台，方便开发者排查
        e.printStackTrace();

        // 获取异常的具体消息
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            message = "服务器内部错误";
        }

        // 返回 400 Bad Request 状态码，并携带 error 字段
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
