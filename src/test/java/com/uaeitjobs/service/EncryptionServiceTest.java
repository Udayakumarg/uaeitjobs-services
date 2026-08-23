package com.uaeitjobs.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    @Test
    void rejectsBlankKey() {
        assertThatThrownBy(() -> new EncryptionService(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void rejectsNullKey() {
        assertThatThrownBy(() -> new EncryptionService(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void encryptsAndDecryptsRoundTrip() {
        EncryptionService service = new EncryptionService("a-real-production-secret");
        String ciphertext = service.encrypt("sk-super-secret-api-key");
        assertThat(service.decrypt(ciphertext)).isEqualTo("sk-super-secret-api-key");
    }
}
