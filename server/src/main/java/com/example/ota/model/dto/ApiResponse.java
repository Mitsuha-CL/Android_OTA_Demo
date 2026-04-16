package com.example.ota.model.dto;

import lombok.Data;

@Data
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String errorCode;
    private String errorMsg;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        response.errorCode = "";
        response.errorMsg = "";
        return response;
    }

    public static <T> ApiResponse<T> error(String errorCode, String errorMsg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.data = null;
        response.errorCode = errorCode;
        response.errorMsg = errorMsg;
        return response;
    }
}
