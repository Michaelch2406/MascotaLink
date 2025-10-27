
package com.mjc.mascotalink.modelo;

import com.google.gson.annotations.SerializedName;

public class ConfirmPasswordResetResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("timestamp")
    private String timestamp;

    public ConfirmPasswordResetResponse() {}

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
