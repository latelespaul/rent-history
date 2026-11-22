package com.rentrix.rentrixserver.location;

import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;

import java.util.UUID;

/**
 * Who is reading location rows, for the {@code PENDING} rule of D-109: a proposed row is seen only by its proposer
 * and by admins. {@code publicId} is null for anonymous callers.
 */
record Viewer(UUID publicId, boolean admin) {
	
	static final Viewer ANONYMOUS = new Viewer(null, false);
	
	static Viewer of(AuthenticatedUser caller) {
		return caller == null ? ANONYMOUS : new Viewer(caller.publicId(), caller.hasRole(Role.ADMIN));
	}
	
	static Viewer of(User user) {
		return new Viewer(user.getPublicId(), user.getRoles().contains(Role.ADMIN));
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
	 * society keep working for the properties already there (D-109).
	 */
	boolean reads(LocationStatus status, User createdBy) {
		return status == LocationStatus.HIDDEN || lists(status, createdBy);
	}
	
}
