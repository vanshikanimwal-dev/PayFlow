package com.payflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

    @Test
    void rfc6238Sha1Vector() {
        assertThat(Totp.hotp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 59 / 30)).isEqualTo("287082");
        assertThat(Totp.matches("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082", Instant.ofEpochSecond(59))).isTrue();
        assertThat(Totp.matches("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "000000", Instant.ofEpochSecond(59))).isFalse();
    }
}
