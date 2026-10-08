package com.payflow.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecipientNamesTest {

    @Test
    void usesTheNameTheyChose() {
        assertThat(RecipientNames.visible("Rahul Sharma", "rahul@payflow.local")).isEqualTo("Rahul Sharma");
    }

    @Test
    void fallsBackToTheEmailName() {
        assertThat(RecipientNames.visible(null, "rahul@payflow.local")).isEqualTo("rahul");
        assertThat(RecipientNames.visible("  ", "rahul@payflow.local")).isEqualTo("rahul");
    }
}
