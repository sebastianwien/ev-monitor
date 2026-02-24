package com.evmonitor.application.spritmonitor;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    private int imported = 0;
    private int skipped = 0;
    private List<String> errors = new ArrayList<>();

    public void incrementImported() {
        imported++;
    }

    public void incrementSkipped() {
        skipped++;
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

    public List<String> getErrors() {
        return errors;
    }
}
