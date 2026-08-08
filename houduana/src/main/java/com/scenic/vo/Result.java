package com.scenic.vo;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static Result success() {
        return new Result<>(200, "success", null);
    }

    public static Result error(String msg) {
        return new Result<>(500, msg, null);
    }
}