
package com.mjc.mascotalink.modelo;

import com.google.gson.annotations.SerializedName;

public class RequestPasswordResetRequest {
    @SerializedName("email")
    private String email;

    public RequestPasswordResetRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
