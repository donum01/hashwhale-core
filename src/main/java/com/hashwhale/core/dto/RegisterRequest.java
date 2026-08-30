package com.hashwhale.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Registration credentials")
public class RegisterRequest {

    @NotBlank
    @Email
    @Schema(example = "satoshi@example.com")
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    @Schema(example = "correct-horse-battery-staple")
    private String password;
}
