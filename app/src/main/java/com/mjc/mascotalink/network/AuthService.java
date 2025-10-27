
package com.mjc.mascotalink.network;

import com.mjc.mascotalink.modelo.ConfirmPasswordResetRequest;
import com.mjc.mascotalink.modelo.ConfirmPasswordResetResponse;
import com.mjc.mascotalink.modelo.RequestPasswordResetRequest;
import com.mjc.mascotalink.modelo.RequestPasswordResetResponse;
import com.mjc.mascotalink.modelo.ValidateTokenResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface AuthService {

    /**
     * POST /api/v1/auth/request-password-reset
     * Solicitar recuperación de contraseña
     */
    @POST("api/v1/auth/request-password-reset")
    Call<RequestPasswordResetResponse> requestPasswordReset(
            @Body RequestPasswordResetRequest request
    );

    /**
     * GET /api/v1/auth/validate-token/{token}
     * Validar token de recuperación
     */
    @GET("api/v1/auth/validate-token/{token}")
    Call<ValidateTokenResponse> validateToken(
            @Path("token") String token
    );

    /**
     * POST /api/v1/auth/confirm-password-reset
     * Confirmar nueva contraseña
     */
    @POST("api/v1/auth/confirm-password-reset")
    Call<ConfirmPasswordResetResponse> confirmPasswordReset(
            @Body ConfirmPasswordResetRequest request
    );
}
