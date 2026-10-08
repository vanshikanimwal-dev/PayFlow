package com.payflow.transfer;

public final class RecipientNames {

    private RecipientNames() {
    }

    public static String visible(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        int at = email.indexOf('@');
        if (at > 0) {
            return email.substring(0, at);
        }
        return email;
    }
}
