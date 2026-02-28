package com.pentamind.config;

import com.pentamind.common.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理 - 捕获并返回具体错误信息
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public R<String> handleException(Exception e) {
        e.printStackTrace();
        // 获取根本原因
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String detail = root.getClass().getSimpleName() + ": " + root.getMessage();
        return R.fail(500, detail);
    }
}
