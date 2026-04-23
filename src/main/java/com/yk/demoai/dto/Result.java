package com.yk.demoai.dto;

import java.util.List;
import java.util.Map;

public class Result<T> {
    private String result;

    private T data;

    private Integer count;

    private String message;

    public Result(String result, T data, Integer count, String message) {
        this.result = result;
        this.data = data;
        this.count = count;
        this.message = message;
    }

    public static <T> Result<T> success(T data) {
        int count = 0;
        if (data instanceof List) {
            count = ((List<?>) data).size();
        } else if (data instanceof Map) {
            count = ((Map<?, ?>) data).size();
        } else if (data != null) {
            count = 1;
        }
        return new Result<>("success", data, count, null);
    }

    public static <T> Result<T> fail(String errorMessage) {
        return new Result<>("fail", null, 0, errorMessage);
    }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
