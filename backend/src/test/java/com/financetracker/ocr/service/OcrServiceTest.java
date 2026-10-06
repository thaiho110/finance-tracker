package com.financetracker.ocr.service;

import com.financetracker.category.service.CategorizationService;
import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.transaction.service.DeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OcrService Unit Tests")
class OcrServiceTest {

    @Mock private CategorizationService categorizationService;
    @Mock private DeduplicationService deduplicationService;

    @InjectMocks private OcrService ocrService;

    @BeforeEach
    void setUp() {
        // @InjectMocks doesn't inject @Value fields; set them manually
        ReflectionTestUtils.setField(ocrService, "imageMaxWidth", 1024);
    }

    @Test
    @DisplayName("Should throw when receipt image is empty")
    void shouldThrowWhenImageEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("image", "receipt.jpg",
            "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> ocrService.processReceipt(emptyFile, UUID.randomUUID()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should throw when image cannot be decoded")
    void shouldThrowWhenInvalidImage() {
        byte[] invalidBytes = "not-an-image".getBytes();
        MockMultipartFile invalidFile = new MockMultipartFile("image", "receipt.jpg",
            "image/jpeg", invalidBytes);

        assertThatThrownBy(() -> ocrService.processReceipt(invalidFile, UUID.randomUUID()))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should process valid receipt image")
    void shouldProcessValidImage() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        byte[] imageBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("image", "receipt.jpg",
            "image/jpeg", imageBytes);

        when(categorizationService.categorize("UNKNOWN MERCHANT")).thenReturn("Other");
        when(deduplicationService.isDuplicate(any(), any(), any())).thenReturn(false);

        ParsedReceiptResponse response = ocrService.processReceipt(file, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.category()).isEqualTo("Other");
    }
}
