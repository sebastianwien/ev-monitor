package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only access to publicly shared user car photos, grouped by model.
 * Backs the public model gallery ({@code /modelle}). Only photos the owner explicitly
 * shared ({@code image_public = true}) are ever exposed - the repository query enforces this.
 */
@Service
@RequiredArgsConstructor
public class PublicCarPhotoService {

    private final CarRepository carRepository;

    /**
     * One entry per model that has at least one public photo, with the newest photo as hero
     * and the total count. Single repository query, grouped in memory.
     */
    public List<ModelPhotoSummary> getPhotoSummaries() {
        Map<String, UUID> heroByModel = new LinkedHashMap<>();
        Map<String, Integer> countByModel = new LinkedHashMap<>();

        // Refs arrive newest-first, so the first ref seen per model is the hero.
        for (CarRepository.CarPhotoRef ref : carRepository.findPublicPhotoRefsNewestFirst()) {
            String model = ref.model();
            heroByModel.putIfAbsent(model, ref.carId());
            countByModel.merge(model, 1, Integer::sum);
        }

        List<ModelPhotoSummary> summaries = new ArrayList<>(heroByModel.size());
        heroByModel.forEach((model, heroCarId) ->
                summaries.add(new ModelPhotoSummary(model, heroCarId, countByModel.get(model))));
        return summaries;
    }

    /**
     * All public photo car ids for a single model, newest first.
     * Returns empty Optional when the model name is not a known enum (→ 404).
     * A valid model with no photos yields a present, empty list (→ 200 []).
     */
    public Optional<List<UUID>> getPublicPhotoCarIds(String modelName) {
        return parseModel(modelName).map(carRepository::findPublicPhotoCarIdsByModel);
    }

    private Optional<CarBrand.CarModel> parseModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(CarBrand.CarModel.valueOf(modelName.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
