package com.rentrix.rentrixserver.city;

import com.rentrix.rentrixserver.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityService {
	
	private final CityRepository cities;
	
	public List<CityResponse> liveCities() {
		return cities.findByLiveTrueOrderByNameAsc().stream().map(CityResponse::from).toList();
	}
	
	/** Rejects any city that is not a live row (D-20). */
	public void requireLive(String code) {
		if (code == null || !cities.existsByCodeAndLiveTrue(code)) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "city_not_live",
				"Rentrix is live only in Bangalore and Pune.");
		}
	}
	
}
