package com.gymflow.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public class NotCommonPasswordValidator implements ConstraintValidator<NotCommonPassword, String> {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012",
            "aaaaaaaaaaaa",
            "111111111111",
            "passwordpassword",
            "password1234",
            "password12345",
            "qwerty123456",
            "qwerty123456789",
            "adminadminadmin",
            "gymflow1234",
            "gymflow2026",
            "contraseña123",
            "contrasena123",
            "contraseña1234",
            "contrasena1234",
            "usuario12345",
            "cliente12345",
            "william12345"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String normalized = normalize(value);
        return !COMMON_PASSWORDS.contains(normalized);
    }

    private String normalize(String value) {
        String withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutDiacritics
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
