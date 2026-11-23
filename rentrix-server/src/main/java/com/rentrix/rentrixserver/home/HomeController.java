package com.rentrix.rentrixserver.home;

import com.rentrix.rentrixserver.home.dto.AddHomeRequest;
import com.rentrix.rentrixserver.home.dto.AddHomeResult;
import com.rentrix.rentrixserver.home.dto.AddressCheck;
import com.rentrix.rentrixserver.home.dto.UpdateHomeRequest;
import com.rentrix.rentrixserver.property.PropertyService;
import com.rentrix.rentrixserver.property.dto.PropertyCard;
import com.rentrix.rentrixserver.property.dto.PropertyDetail;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** "Add your home" for any signed-in user (D-139): the duplicate check and the one-step add. */
@RestController
@RequiredArgsConstructor
public class HomeController {
	
	private final HomeService homes;
	private final PropertyService properties;
	
	/** {@code GET /me/homes/matches}: "Is it this one?" before a new home is added. */
	@GetMapping("/me/homes/matches")
	public List<PropertyCard> matches(@RequestParam String city, @RequestParam String pincode,
		@RequestParam(required = false) UUID society, @RequestParam(name = "societyName", required = false) String name,
		@RequestParam String unit) {
		return properties.matches(city, pincode, society, name, unit);
	}
	
	/** {@code GET /me/homes/address-check}: step 1's PIN code against the city (blocks) and the area (warns). */
	@GetMapping("/me/homes/address-check")
	public AddressCheck addressCheck(@RequestParam String city, @RequestParam String pincode,
		@RequestParam(required = false) UUID area, @AuthenticationPrincipal AuthenticatedUser caller) {
		return homes.checkAddress(city, pincode, area, caller);
	}
	
	/** {@code POST /me/homes}: the home, the caller's tenancy and rent, and the owner's contact, in one step. */
	@PostMapping("/me/homes")
	public ResponseEntity<AddHomeResult> add(@Valid @RequestBody AddHomeRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		AddHomeResult result = homes.add(request, caller);
		return ResponseEntity.created(URI.create("/api/v1/properties/" + result.property().id())).body(result);
	}
	
	/** {@code PATCH /me/homes/{id}}: the person who added the home corrects its address or size (D-156). */
	@PatchMapping("/me/homes/{id}")
	public PropertyDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateHomeRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		return homes.update(id, request, caller);
	}
	
	/** {@code DELETE /me/homes/{id}}: the person who added the home removes it with their own records (D-156). */
	@DeleteMapping("/me/homes/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
		homes.delete(id, caller);
		return ResponseEntity.noContent().build();
	}
	
}
