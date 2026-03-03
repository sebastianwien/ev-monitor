package com.evmonitor.infrastructure.web;

import com.evmonitor.application.CarResponse;
import com.evmonitor.domain.Car;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.domain.User;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for car image upload, retrieval, and visibility.
 *
 * SECURITY CRITICAL:
 * - Only the car owner may upload/delete images
 * - Private images (imagePublic=false) must not be readable by other users
 * - Public images must be readable by any authenticated user
 */
class CarImageControllerIntegrationTest extends AbstractIntegrationTest {

    @Value("${app.car-images.directory}")
    private String uploadDirectory;

    private User owner;
    private User otherUser;
    private Car ownerCar;

    @BeforeEach
    void setUpTestData() {
        owner = createAndSaveUser("img-owner-" + System.nanoTime() + "@example.com");
        otherUser = createAndSaveUser("img-other-" + System.nanoTime() + "@example.com");
        ownerCar = createAndSaveCar(owner.getId(), CarBrand.CarModel.MODEL_3);
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    @Test
    void ownerCanUploadImage_Returns201WithImageUrl() throws IOException {
        HttpEntity<MultiValueMap<String, Object>> request = buildUploadRequest(
                owner, createMinimalJpeg(), "image/jpeg", false);

        ResponseEntity<CarResponse> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=false",
                HttpMethod.POST,
                request,
                CarResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().imageUrl(), "imageUrl must be set after upload");
        assertEquals("/api/cars/" + ownerCar.getId() + "/image", response.getBody().imageUrl());
        assertFalse(response.getBody().imagePublic());
    }

    @Test
    void nonOwnerCannotUploadImage_Returns400() throws IOException {
        HttpEntity<MultiValueMap<String, Object>> request = buildUploadRequest(
                otherUser, createMinimalJpeg(), "image/jpeg", false);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=false",
                HttpMethod.POST,
                request,
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // Verify no image was saved on the car
        Car carInDb = carRepository.findById(ownerCar.getId()).orElseThrow();
        assertNull(carInDb.getImagePath(), "Image path must remain null after rejected upload");
    }

    @Test
    void uploadWithInvalidContentType_Returns400() {
        // Send a text file disguised as an image
        HttpHeaders headers = createAuthHeaders(owner.getId(), owner.getEmail());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fakeFile = new ByteArrayResource("not an image".getBytes()) {
            @Override
            public String getFilename() { return "hack.txt"; }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.TEXT_PLAIN);
        body.add("file", new HttpEntity<>(fakeFile, partHeaders));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=false",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Re-upload overwrites previous image
    // -------------------------------------------------------------------------

    @Test
    void reUploadOverwritesPreviousImage() throws IOException {
        // First upload
        HttpEntity<MultiValueMap<String, Object>> first = buildUploadRequest(
                owner, createMinimalJpeg(), "image/jpeg", false);
        restTemplate.exchange("/api/cars/" + ownerCar.getId() + "/image?isPublic=false",
                HttpMethod.POST, first, CarResponse.class);

        String pathAfterFirstUpload = carRepository.findById(ownerCar.getId())
                .orElseThrow().getImagePath();
        assertNotNull(pathAfterFirstUpload);

        // Second upload (same car, different isPublic)
        HttpEntity<MultiValueMap<String, Object>> second = buildUploadRequest(
                owner, createMinimalJpeg(), "image/jpeg", true);
        ResponseEntity<CarResponse> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=true",
                HttpMethod.POST, second, CarResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().imagePublic(), "isPublic flag must be updated on re-upload");

        // Only one file should exist — same path (UUID-based, no versioning)
        String pathAfterSecondUpload = carRepository.findById(ownerCar.getId())
                .orElseThrow().getImagePath();
        assertEquals(pathAfterFirstUpload, pathAfterSecondUpload,
                "File path must stay the same (overwrite, no versioning)");
    }

    // -------------------------------------------------------------------------
    // GET image — visibility
    // -------------------------------------------------------------------------

    @Test
    void ownerCanGetOwnPrivateImage() throws IOException {
        uploadImage(owner, ownerCar, false);

        HttpEntity<Void> request = createAuthRequest(owner.getId(), owner.getEmail());
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image",
                HttpMethod.GET, request, byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void otherUserCannotGetPrivateImage_Returns403() throws IOException {
        uploadImage(owner, ownerCar, false); // private

        HttpEntity<Void> request = createAuthRequest(otherUser.getId(), otherUser.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image",
                HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void otherUserCanGetPublicImage_Returns200() throws IOException {
        uploadImage(owner, ownerCar, true); // public

        HttpEntity<Void> request = createAuthRequest(otherUser.getId(), otherUser.getEmail());
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image",
                HttpMethod.GET, request, byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void getImage_WhenNoImageUploaded_Returns404() {
        // No image uploaded for ownerCar
        HttpEntity<Void> request = createAuthRequest(owner.getId(), owner.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image",
                HttpMethod.GET, request, String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // PATCH image — update visibility
    // -------------------------------------------------------------------------

    @Test
    void ownerCanUpdateImageVisibility() throws IOException {
        uploadImage(owner, ownerCar, false); // uploaded as private

        HttpEntity<Void> request = createAuthRequest(owner.getId(), owner.getEmail());
        ResponseEntity<CarResponse> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=true",
                HttpMethod.PATCH, request, CarResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().imagePublic(), "imagePublic must be true after PATCH");

        Car carInDb = carRepository.findById(ownerCar.getId()).orElseThrow();
        assertTrue(carInDb.isImagePublic(), "imagePublic must be persisted in DB");
        assertNotNull(carInDb.getImagePath(), "imagePath must not be cleared by visibility update");
    }

    @Test
    void nonOwnerCannotUpdateImageVisibility_Returns400() throws IOException {
        uploadImage(owner, ownerCar, false);

        HttpEntity<Void> request = createAuthRequest(otherUser.getId(), otherUser.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=true",
                HttpMethod.PATCH, request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        Car carInDb = carRepository.findById(ownerCar.getId()).orElseThrow();
        assertFalse(carInDb.isImagePublic(), "imagePublic must not be changed by non-owner");
    }

    @Test
    void patchVisibility_WithoutImage_Returns400() {
        // No image on ownerCar
        HttpEntity<Void> request = createAuthRequest(owner.getId(), owner.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image?isPublic=true",
                HttpMethod.PATCH, request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // DELETE image
    // -------------------------------------------------------------------------

    @Test
    void ownerCanDeleteImage_Returns204AndRemovesFileFromDisk() throws IOException {
        uploadImage(owner, ownerCar, false);

        // Verify file exists on disk before deletion
        File imageFile = imageFileFor(ownerCar.getId());
        assertTrue(imageFile.exists(), "Image file must exist on disk after upload");

        HttpEntity<Void> request = createAuthRequest(owner.getId(), owner.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image",
                HttpMethod.DELETE, request, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // DB: imagePath cleared
        Car carInDb = carRepository.findById(ownerCar.getId()).orElseThrow();
        assertNull(carInDb.getImagePath(), "imagePath must be null after deletion");
        assertFalse(carInDb.isImagePublic(), "imagePublic must be reset to false after deletion");

        // Filesystem: file gone
        assertFalse(imageFile.exists(), "Image file must be deleted from disk");
    }

    @Test
    void nonOwnerCannotDeleteImage_Returns400() throws IOException {
        uploadImage(owner, ownerCar, false);

        HttpEntity<Void> request = createAuthRequest(otherUser.getId(), otherUser.getEmail());
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId() + "/image",
                HttpMethod.DELETE, request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // DB: image still set
        Car carInDb = carRepository.findById(ownerCar.getId()).orElseThrow();
        assertNotNull(carInDb.getImagePath(), "Image must not be deleted by non-owner");

        // Filesystem: file still there
        assertTrue(imageFileFor(ownerCar.getId()).exists(), "Image file must remain on disk");
    }

    @Test
    void deletingCarAlsoDeletesImageFromDisk() throws IOException {
        uploadImage(owner, ownerCar, false);
        File imageFile = imageFileFor(ownerCar.getId());
        assertTrue(imageFile.exists(), "Image file must exist before car deletion");

        HttpEntity<Void> request = createAuthRequest(owner.getId(), owner.getEmail());
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/cars/" + ownerCar.getId(),
                HttpMethod.DELETE, request, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(imageFile.exists(), "Image file must be deleted from disk when car is deleted");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void uploadImage(User user, Car car, boolean isPublic) throws IOException {
        HttpEntity<MultiValueMap<String, Object>> request = buildUploadRequest(
                user, createMinimalJpeg(), "image/jpeg", isPublic);
        restTemplate.exchange(
                "/api/cars/" + car.getId() + "/image?isPublic=" + isPublic,
                HttpMethod.POST, request, CarResponse.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> buildUploadRequest(
            User user, byte[] imageBytes, String contentType, boolean isPublic) {
        HttpHeaders headers = createAuthHeaders(user.getId(), user.getEmail());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() { return "test.jpg"; }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(fileResource, partHeaders));

        return new HttpEntity<>(body, headers);
    }

    /**
     * Creates a minimal valid 10x10 JPEG image as bytes for upload tests.
     */
    private byte[] createMinimalJpeg() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** Returns the expected on-disk file for a car image. */
    private File imageFileFor(UUID carId) {
        return new File(uploadDirectory, carId + ".jpg");
    }
}
