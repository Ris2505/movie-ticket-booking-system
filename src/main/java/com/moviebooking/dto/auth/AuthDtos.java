package com.moviebooking.dto.auth;

import com.moviebooking.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDtos {

    @Data
    public static class RegisterRequest {
        @NotBlank @Email
        private String email;
        @NotBlank @Size(min = 8, max = 100)
        private String password;
        @NotNull
        private UserRole role;
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    public static class UserResponse {
        private Long id;
        private String email;
        private UserRole role;

        public static UserResponse of(Long id, String email, UserRole role) {
            var r = new UserResponse();
            r.id = id;
            r.email = email;
            r.role = role;
            return r;
        }
    }
}
