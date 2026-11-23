package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Who is reading location rows, for the {@code PENDING} rule of D-109: a proposed row is listed only for its
 * proposer and admins, and read by id also by a landlord with a home on it. {@code publicId} is null for anonymous
 * callers.
 */
record Viewer(UUID publicId, boolean admin) {
	
	static final Viewer ANONYMOUS = new Viewer(null, false);
	
	static Viewer of(AuthenticatedUser caller) {
		return caller == null ? ANONYMOUS : new Viewer(caller.publicId(), caller.hasRole(Role.ADMIN));
	}
	
	/** A writer on the main site, which never has admin powers (D-19): their proposals are always {@code PENDING}. */
	static Viewer of(User user) {
		return new Viewer(user.getPublicId(), false);
	}
	
	boolean isProposer(User createdBy) {
		return publicId != null && createdBy != null && publicId.equals(createdBy.getPublicId());
	}
	
	/** Lists (browse, pickers): {@code ACTIVE}, plus {@code PENDING} rows the viewer proposed (admins: all). */
	boolean lists(LocationStatus status, User createdBy) {
		return status == LocationStatus.ACTIVE
			|| status == LocationStatus.PENDING && (admin || isProposer(createdBy));
	}
	
	/**
	 * Reads by id: everything a list shows, plus {@code HIDDEN} rows, so links and filters on a hidden area or
	 * society keep working for the properties already there, plus {@code PENDING} rows that hold one of the
	 * viewer's own homes, so a landlord who joined another's proposal can reuse it by id (D-109).
	 * {@code holdsOwnHome} is asked only for such a {@code PENDING} row.
	 */
	boolean reads(LocationStatus status, User createdBy, BooleanSupplier holdsOwnHome) {
		return status == LocationStatus.HIDDEN || lists(status, createdBy)
			|| status == LocationStatus.PENDING && publicId != null && holdsOwnHome.getAsBoolean();
	}
	
}
