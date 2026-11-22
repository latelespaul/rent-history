package com.rentrix.rentrixserver.home;

import com.rentrix.rentrixserver.home.dto.AddHomeRequest;
import com.rentrix.rentrixserver.home.dto.AddHomeResult;
import com.rentrix.rentrixserver.property.PropertyService;
import com.rentrix.rentrixserver.property.dto.PropertyCard;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
	
	/** {@code POST /me/homes}: the home, the caller's tenancy and rent, and the owner's contact, in one step. */
	@PostMapping("/me/homes")
	public ResponseEntity<AddHomeResult> add(@Valid @RequestBody AddHomeRequest request,
		@AuthenticationPrincipal AuthenticatedUser caller) {
		AddHomeResult result = homes.add(request, caller);
		return ResponseEntity.created(URI.create("/api/v1/properties/" + result.property().id())).body(result);
	}
	
}
