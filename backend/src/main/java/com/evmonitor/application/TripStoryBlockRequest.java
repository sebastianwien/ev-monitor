package com.evmonitor.application;

import java.util.UUID;

/**
 * Client-side block payload. Trip widgets carry only the trip id and a free-text label -
 * the stats themselves are snapshotted server-side and never accepted from the client.
 */
public record TripStoryBlockRequest(
        String type,
        String markdown,
        UUID tripId,
        String label
) {
}
