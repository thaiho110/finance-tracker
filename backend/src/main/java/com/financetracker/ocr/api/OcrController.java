package com.financetracker.ocr.api;

import com.financetracker.ocr.dto.ParsedReceiptResponse;
import com.financetracker.ocr.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ocr")
@RequiredArgsConstructor
@Tag(name = "Receipt OCR", description = "Upload receipt images for OCR extraction")
public class OcrController {

    private final OcrService ocrService;

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a receipt image and extract transaction data")
    public ResponseEntity<ParsedReceiptResponse> processReceipt(
            @RequestParam("image") MultipartFile image,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (image.isEmpty()) {
            throw new IllegalArgumentException("Receipt image is empty");
        }

        UUID userId = UUID.nameUUIDFromBytes(userDetails.getUsername().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok(ocrService.processReceipt(image, userId));
    }
}
