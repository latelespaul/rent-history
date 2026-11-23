package com.rentrix.rentrixserver.agreement;

import com.rentrix.rentrixserver.agreement.dto.AgreementView;
import com.rentrix.rentrixserver.agreement.dto.SignedUrlView;
import com.rentrix.rentrixserver.agreement.dto.UploadAgreementRequest;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * Rental agreements for the parties of a tenancy (§12.2, D-123 – D-131). Upload is multipart: {@code file} (PDF,
 * JPEG, PNG or HEIC, ≤ 10 MB) and {@code data} (JSON {@link UploadAgreementRequest}).
 */
@RestController
@RequiredArgsConstructor
public class AgreementController {
	
	private final AgreementService agreements;
	
	@PostMapping(path = "/me/agreements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<AgreementView> upload(@RequestPart(value = "file", required = false) MultipartFile file,
		@Valid @RequestPart("data") UploadAgreementRequest data, @AuthenticationPrincipal AuthenticatedUser caller) {
		AgreementView created = agreements.upload(data, file, caller);
		return ResponseEntity.created(URI.create("/api/v1/me/agreements/" + created.id())).body(created);
	}
	
	@GetMapping("/me/agreements/{id}")
	public AgreementView get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return agreements.get(id, caller);
	}
	
	@PatchMapping("/me/agreements/{id}")
	public AgreementView correct(@PathVariable UUID id, @Valid @RequestBody AgreementFields fields,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return agreements.correct(id, fields, caller);
	}
	
	@PostMapping("/me/agreements/{id}/confirm")
	public AgreementView confirm(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return agreements.confirm(id, caller);
	}
	
	@GetMapping("/me/agreements/{id}/url")
	public SignedUrlView url(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return agreements.url(id, caller);
	}
	
	@DeleteMapping("/me/agreements/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		agreements.delete(id, caller);
		return ResponseEntity.noContent().build();
	}
	
	@GetMapping("/tenancies/{id}/agreement")
	public AgreementView forTenancy(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		return agreements.forTenancy(id, caller);
	}
	
}
