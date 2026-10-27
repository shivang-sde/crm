package com.shivang.crm.modules.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class ProductionSecuritySecretsValidatorTest {

    private static final String SHIPPED_JWT_DEFAULT =
            "Y3JtLWFwcGxpY2F0aW9uLXN1cGVyLXNlY3JldC1rZXktdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTY=";
    private static final String SHIPPED_ENC_DEFAULT = "XKM1/RmCg8xvU/iYTEY81mZUgZDWeHxwH3Cpqwz1D2w=";

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void rejectsTheJwtDefaultThatShipsInTheRepository() {
        var validator = new ProductionSecuritySecretsValidator(SHIPPED_JWT_DEFAULT, validEncryptionKey());

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining(SHIPPED_JWT_DEFAULT);
    }

    @Test
    void rejectsTheEncryptionDefaultThatShipsInTheRepository() {
        var validator = new ProductionSecuritySecretsValidator(validJwtSecret(), SHIPPED_ENC_DEFAULT);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTEGRATION_ENCRYPTION_KEY")
                .hasMessageNotContaining(SHIPPED_ENC_DEFAULT);
    }

    @Test
    void rejectsMissingJwtSecret() {
        var validator = new ProductionSecuritySecretsValidator("", validEncryptionKey());

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void rejectsWeakJwtSecret() {
        var validator = new ProductionSecuritySecretsValidator(b64("too-short".getBytes()), validEncryptionKey());

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsNonBase64JwtSecret() {
        var validator = new ProductionSecuritySecretsValidator("not-base64!!", validEncryptionKey());

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void rejectsWrongLengthEncryptionKey() {
        var validator = new ProductionSecuritySecretsValidator(validJwtSecret(), b64(new byte[16]));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }

    @Test
    void acceptsStrongUniqueSecrets() {
        var validator = new ProductionSecuritySecretsValidator(validJwtSecret(), validEncryptionKey());

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void errorMessagesNeverLeakSecretMaterial() {
        var validator = new ProductionSecuritySecretsValidator(SHIPPED_JWT_DEFAULT, validEncryptionKey());

        assertThatThrownBy(validator::validate)
                .satisfies(thrown -> assertThat(thrown.getMessage())
                        .doesNotContain(SHIPPED_JWT_DEFAULT)
                        .doesNotContain(validEncryptionKey()));
    }

    @Test
    void validatorIsScopedToTheProdProfile() {
        assertThat(ProductionSecuritySecretsValidator.class.getAnnotation(org.springframework.context.annotation.Profile.class))
                .as("dev/test keep their deterministic local secrets")
                .isNotNull();
        assertThat(ProductionSecuritySecretsValidator.class
                .getAnnotation(org.springframework.context.annotation.Profile.class).value())
                .containsExactly("prod");
    }

    private static String validJwtSecret() {
        return b64("a-32-byte-minimum-secret-value-01".getBytes());
    }

    private static String validEncryptionKey() {
        return b64(new byte[32]);
    }
}
