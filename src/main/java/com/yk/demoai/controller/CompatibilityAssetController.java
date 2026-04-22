package com.yk.demoai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 兼容历史页面对旧静态资源路径的访问，避免浏览器缓存旧页面时直接报 404。
 */
@Controller
public class CompatibilityAssetController {

    /**
     * 浏览器通常会自动探测 favicon，这里返回空响应即可避免无意义的 404 日志。
     */
    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
