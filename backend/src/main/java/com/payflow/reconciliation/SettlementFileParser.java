package com.payflow.reconciliation;

import java.util.ArrayList;
import java.util.List;

public final class SettlementFileParser {

    private SettlementFileParser() {
    }

    public record Row(String paymentId, String reference, long amountMinor, String status) {
    }

    public static List<Row> parse(String csv) {
        List<Row> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.split("\\R");
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("payment_id")) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 4) {
                continue;
            }
            try {
                rows.add(new Row(columns[0].trim(), columns[1].trim(), Long.parseLong(columns[2].trim()), columns[3].trim()));
            } catch (NumberFormatException ex) {
                // Skip a drifted or corrupt line; the missing local row is classified later if we have it.
            }
        }
        return rows;
    }
}
