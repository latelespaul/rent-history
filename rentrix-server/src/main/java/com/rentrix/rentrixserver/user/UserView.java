package com.rentrix.rentrixserver.user;

import com.rentrix.rentrixserver.identity.Badges;
import com.rentrix.rentrixserver.verification.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The signed-in user's own account, as returned by {@code GET /me} and the sign-in endpoints. Private fields
 * (email, phone) are shown only to the user themselves (D-57); the phone only masked, e.g. {@code +91 XXXXXX3210}
 * (D-106).
 *
 * @param shortName       the public short name ({@link PublicNames#shortName}), shown as-is by the web
 * @param needsOnboarding true until the first-sign-in role pick is done (D-73)
 * @param preferredMode  {@code TENANT} or {@code OWNER}, the header mode the user last picked; null if never (PLAN R10)
 * @param verification    approval status per held {@code TENANT} / {@code LANDLORD} role (D-89, D-91)
 * @param badges          "ID verified" (any one of Aadhaar, PAN or phone OTP) and the optional "LinkedIn verified"
 *                        (D-99, D-100); the same for both roles
 */
public record UserView(
	UUID id,
	String displayName,
	String shortName,
	String email,
	boolean emailVerified,
	String avatarUrl,
	String phone,
	List<Role> roles,
	String homeCity,
	String preferredMode,
	UserStatus status,
	boolean demo,
	boolean needsOnboarding,
	Instant onboardedAt,
	Map<Role, VerificationStatus> verification,
	Badges badges,
	Instant createdAt
) {
}
