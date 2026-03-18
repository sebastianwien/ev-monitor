package com.evmonitor.application.spritmonitor;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    private int imported = 0;
    private int skipped = 0;
    private int coinsAwarded = 0;
    private int withoutLocation = 0;
    private List<String> errors = new ArrayList<>();

    public void incrementImported() {
        imported++;
    }

    public void incrementSkipped() {
        skipped++;
    }

    public void addCoinsAwarded(int coins) {
        coinsAwarded += coins;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public int getImported() {
        return imported;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getCoinsAwarded() {
        return coinsAwarded;
    }

    public void incrementWithoutLocation() {
        withoutLocation++;
    }

    public int getWithoutLocation() {
        return withoutLocation;
    }

    public List<String> getErrors() {
        return errors;
    }
}
