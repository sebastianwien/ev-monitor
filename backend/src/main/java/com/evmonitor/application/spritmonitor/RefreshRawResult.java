package com.evmonitor.application.spritmonitor;

import java.util.List;

public record RefreshRawResult(int refreshed, int skipped, List<String> errors) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int refreshed = 0;
        private int skipped = 0;
        private final java.util.List<String> errors = new java.util.ArrayList<>();

        public void incrementRefreshed() { refreshed++; }
        public void incrementSkipped() { skipped++; }
        public void addError(String error) { errors.add(error); }

        public RefreshRawResult build() {
            return new RefreshRawResult(refreshed, skipped, List.copyOf(errors));
        }
    }
}
