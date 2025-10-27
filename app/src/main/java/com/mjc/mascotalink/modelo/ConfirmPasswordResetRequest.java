
package com.mjc.mascotalink.modelo;

import com.google.gson.annotations.SerializedName;

public class ConfirmPasswordResetRequest {
    @SerializedName("token")
    private String token;

    @SerializedName("new_password")
    private String newPassword;

    @SerializedName("confirm_password")
    private String confirmPassword;

    public ConfirmPasswordResetRequest(String token, String newPassword, String confirmPassword) {
        this.token = token;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getToken() {
        return token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }
}
