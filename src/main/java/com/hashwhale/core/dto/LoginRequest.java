package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Login credentials")
public class LoginRequest {

    @NotBlank
    @Email
    @Schema(example = "satoshi@example.com")
    private String email;

    @NotBlank
    @Schema(example = "correct-horse-battery-staple")
    private String password;
}
