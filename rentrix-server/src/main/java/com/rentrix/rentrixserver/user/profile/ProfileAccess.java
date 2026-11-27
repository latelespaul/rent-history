package com.rentrix.rentrixserver.user.profile;

import com.rentrix.rentrixserver.enquiry.EnquiryStatus;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.tenancy.TenancyParty;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Who may see a person's profile and the reviews about them (§10).
 * <ul>
 *   <li>D-55: a landlord profile (short name, landlord rating, reviews received as landlord) is open to every
 *       signed-in user, approved or not; anonymous visitors see a landlord only on property pages.</li>
 *   <li>D-54: tenants are not searchable. A tenant profile and the reviews about them as tenant are visible to the
 *       tenant, to landlords of their tenancies (confirmed, or requested by the tenant), to a landlord holding an
 *       open enquiry from them, and to admins. Share links (PLAN B7) open it through their own endpoint.</li>
 *   <li>D-175: the reviews owners wrote about a tenant, with their count and rating, go only to the owners D-54
 *       allows and to admins: never to the tenant they are about, another tenant or a signed-out visitor.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileAccess {
	
	private static final List<TenancyStatus> CONFIRMED = List.of(TenancyStatus.ACTIVE, TenancyStatus.ENDED);
	
	private final ProfileAccessRepository repository;
	private final Clock clock;
	
	/** A user has a landlord profile once they hold the role. */
	public boolean isLandlord(User user) {
		return user.getDeletedAt() == null && user.hasRole(Role.LANDLORD);
	}
	
	/** D-54 for a signed-in {@code viewer}; {@code admin} comes from the caller's token. */
	public boolean canSeeTenant(User viewer, User tenant, boolean admin) {
		if (tenant.getDeletedAt() != null) {
			return admin;
		}
		if (admin || viewer.getId().equals(tenant.getId())) {
			return true;
		}
		return repository.landlordOfTenant(viewer.getId(), tenant.getId(), CONFIRMED,
			TenancyStatus.PENDING_CONFIRMATION, TenancyParty.TENANT)
			|| repository.openEnquiry(viewer.getId(), tenant.getId(), EnquiryStatus.OPEN, clock.instant());
	}
	
	/**
	 * D-175: may {@code viewer} read the owners' reviews about {@code tenant}, their count and their rating? Only an
	 * owner D-54 allows, or an admin; never the tenant themself, whatever their roles.
	 */
	public boolean canReadTenantReviews(User viewer, User tenant, boolean admin) {
		return !viewer.getId().equals(tenant.getId()) && canSeeTenant(viewer, tenant, admin);
	}
	
	public long listedHomes(User landlord) {
		return repository.countListed(landlord.getId(), PropertyStatus.PUBLISHED);
	}
	
}
