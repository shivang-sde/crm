package com.shivang.crm.modules.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FeedbackTokenServiceTest {

    private final FeedbackTokenService service = new FeedbackTokenService();

    @Test
    void tokensAreUrlSafeAndHighEntropy() {
        String first = service.generateToken();
        String second = service.generateToken();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashIsStableSha256Hex() {
        assertThat(service.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void tokenPathIsRelativeToThePublicRoute() {
        assertThat(service.tokenPath("abc")).isEqualTo("/feedback/abc");
    }
}
