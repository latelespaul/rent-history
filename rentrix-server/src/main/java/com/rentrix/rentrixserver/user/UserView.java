package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.verification.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The signed-in user's own account, as returned by {@code GET /me} and the sign-in endpoints. Private fields
 * (email, phone) are shown only to the user themselves (D-57).
 *
 * @param needsOnboarding true until the first-sign-in role pick is done (D-73)
 * @param verification    approval status per held {@code TENANT} / {@code LANDLORD} role (D-89, D-91)
 */
public record UserView(
	UUID id,
	String displayName,
	String email,
	boolean emailVerified,
	String avatarUrl,
	String phone,
	List<Role> roles,
	String homeCity,
	UserStatus status,
	boolean demo,
	boolean needsOnboarding,
	Instant onboardedAt,
	Map<Role, VerificationStatus> verification,
	Instant createdAt
) {
}
