package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.integration.exception.CredentialEncryptionException;
import com.shivang.crm.modules.integration.service.impl.DefaultCredentialEncryptionService;

class DefaultCredentialEncryptionServiceTest {

    private final DefaultCredentialEncryptionService encryptionService = new DefaultCredentialEncryptionService("AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");

    @Test
    void encryptThenDecryptReturnsOriginalValue() {
        String original = "super-secret-token";

        String encrypted = encryptionService.encrypt(original);

        assertThat(encrypted).isNotBlank();
        assertThat(encryptionService.decrypt(encrypted)).isEqualTo(original);
    }

    @Test
    void samePlaintextProducesDifferentEncryptedValuesBecauseIvIsRandom() {
        String original = "same-secret";

        String first = encryptionService.encrypt(original);
        String second = encryptionService.encrypt(original);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void decryptInvalidDataFailsSafely() {
        assertThatThrownBy(() -> encryptionService.decrypt("invalid-value"))
            .isInstanceOf(CredentialEncryptionException.class);
    }

    @Test
    void encryptionServiceDoesNotReturnPlaintext() {
        String original = "top-secret";

        String encrypted = encryptionService.encrypt(original);

        assertThat(encrypted).doesNotContain(original);
    }
}
