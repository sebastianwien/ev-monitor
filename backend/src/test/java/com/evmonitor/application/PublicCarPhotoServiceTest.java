package com.evmonitor.application;

import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.CarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCarPhotoServiceTest {

    @Mock
    private CarRepository carRepository;

    @InjectMocks
    private PublicCarPhotoService service;

    @Test
    void summariesUseNewestAsHeroAndCountPerModel() {
        UUID m3Newest = UUID.randomUUID();
        UUID m3Older = UUID.randomUUID();
        UUID mY = UUID.randomUUID();
        // Repository contract: newest first.
        when(carRepository.findPublicPhotoRefsNewestFirst()).thenReturn(List.of(
                new CarRepository.CarPhotoRef(m3Newest, "MODEL_3"),
                new CarRepository.CarPhotoRef(m3Older, "MODEL_3"),
                new CarRepository.CarPhotoRef(mY, "MODEL_Y")
        ));

        List<ModelPhotoSummary> summaries = service.getPhotoSummaries();

        assertThat(summaries).containsExactlyInAnyOrder(
                new ModelPhotoSummary("MODEL_3", m3Newest, 2),
                new ModelPhotoSummary("MODEL_Y", mY, 1)
        );
    }

    @Test
    void unknownModelNameYieldsEmptyOptional() {
        assertThat(service.getPublicPhotoCarIds("NOT_A_REAL_MODEL")).isEmpty();
        assertThat(service.getPublicPhotoCarIds(null)).isEmpty();
        assertThat(service.getPublicPhotoCarIds("  ")).isEmpty();
    }

    @Test
    void validModelDelegatesToRepository() {
        UUID id = UUID.randomUUID();
        when(carRepository.findPublicPhotoCarIdsByModel(CarBrand.CarModel.MODEL_3))
                .thenReturn(List.of(id));

        assertThat(service.getPublicPhotoCarIds("MODEL_3")).contains(List.of(id));
    }
}
