package com.rentrix.rentrixserver.admin;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.rating.RatingRecomputer;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /admin/ratings/recompute} (D-163): runs the nightly rating repair ({@link RatingRecomputer#recomputeAll()},
 * D-33) now, so stored aggregates follow the headline mode ({@code app.ratings.headline}, D-135) right after a switch.
 * {@code /admin/**} answers only on the admin host and only to an admin console session (§13.3); the caller's
 * membership is re-checked in the database (D-19).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminRatingRepairController {

	public record RecomputeView(int rebuilt) {
	}

	private final RatingRecomputer recomputer;
	private final UserRepository users;
	private final AdminConsoleMembers members;

	@PostMapping("/admin/ratings/recompute")
	public RecomputeView recompute(@AuthenticationPrincipal AuthenticatedUser caller) {
		users.findByPublicId(caller.publicId()).filter(members::isMember)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You are not allowed to do this."));
		int rebuilt = recomputer.recomputeAll();
		log.info("Admin {} rebuilt {} rating aggregates", caller.publicId(), rebuilt);
		return new RecomputeView(rebuilt);
	}

}
