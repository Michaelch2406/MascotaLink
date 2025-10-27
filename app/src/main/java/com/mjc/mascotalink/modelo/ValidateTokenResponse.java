
package com.mjc.mascotalink.modelo;

import com.google.gson.annotations.SerializedName;

public class ValidateTokenResponse {
    @SerializedName("valid")
    private boolean valid;

    @SerializedName("message")
    private String message;

    public ValidateTokenResponse() {}

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }
}
