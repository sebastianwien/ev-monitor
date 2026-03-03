package com.evmonitor.application;

/**
 * Response for car image upload and visibility change endpoints.
 * Includes the updated car and coins awarded for the action.
 */
public record CarImageResponse(CarResponse car, int coinsAwarded) {}
