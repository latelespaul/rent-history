package com.rentrix.rentrixserver.account;

import com.rentrix.rentrixserver.admin.AuditLog;
import com.rentrix.rentrixserver.admin.ModerationActionType;
import com.rentrix.rentrixserver.admin.ModerationTargetType;
import com.rentrix.rentrixserver.auth.RefreshTokenService;
import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.property.UnclaimedLandlord;
import com.rentrix.rentrixserver.security.AuthenticatedUser;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.PublicNames;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * {@code DELETE /me}: the user deletes their own account, at once and for good (D-58, D-157 – D-160,
 * {@code rentrix-docs/design/delete-account.md}). One transaction erases the personal data, anonymises what other
 * people's records still need as "Deleted user", ends every session and writes one audit row with the user id and
 * time only. Nothing here logs a name, email or phone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {
	
	/** The word the user types to confirm (D-157). */
	public static final String CONFIRM_WORD = "DELETE";
	/** How recent the session's sign-in must be (D-157). */
	public static final Duration REAUTH_WINDOW = Duration.ofMinutes(10);
	/** What a kept phone code row holds instead of the number (D-159). */
	static final String ERASED_PHONE = "erased";
	
	private final UserRepository users;
	private final RefreshTokenService refreshTokens;
	private final UnclaimedLandlord unclaimed;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	
	@PersistenceContext
	private EntityManager em;
	
	@Transactional
	public void delete(AuthenticatedUser caller, String confirm) {
		if (!CONFIRM_WORD.equals(confirm)) {
			throw ApiException.rule("confirm_mismatch", "Type DELETE to confirm.");
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		if (caller.authTime() == null || caller.authTime().isBefore(now.minus(REAUTH_WINDOW))) {
			throw new ApiException(HttpStatus.FORBIDDEN, "reauth_required",
				"For your safety, sign in again, then delete your account.");
		}
		User user = users.lockByPublicId(caller.publicId())
			.filter(u -> u.getStatus() != UserStatus.DELETED)
			.orElseThrow(() -> ApiException.notFound("User"));
		if (UnclaimedLandlord.is(user)) {
			throw ApiException.notFound("User");
		}
		em.flush();
		
		// Sessions: no device can refresh; the JWT filter refuses live access tokens once the status commits (D-160).
		refreshTokens.deleteAll(user);
		
		// Identity and verification records: phone, Aadhaar and PAN last 4 + HMAC, LinkedIn URL, applications.
		bulk("delete from IdentityVerification v where v.user = :user", user);
		bulk("delete from VerificationEvent e where e.application.id in"
			+ " (select a.id from VerificationApplication a where a.user = :user)", user);
		bulk("delete from VerificationApplication a where a.user = :user", user);
		
		// Phone codes: the number goes, the keyed hash and send time stay for the per-number daily limit until the
		// nightly cleanup (within 48 h); every code is used up.
		em.createQuery("update PhoneOtpChallenge c set c.phoneE164 = :erased, c.gatewayRequestId = null,"
				+ " c.consumedAt = coalesce(c.consumedAt, :now), c.updatedAt = :now where c.user = :user")
			.setParameter("erased", ERASED_PHONE).setParameter("now", now).setParameter("user", user).executeUpdate();
		
		bulk("delete from ShareLink s where s.user = :user", user);
		// Invite friends (IF-8): the code goes, and the user no longer counts for anyone or counts anyone.
		bulk("delete from Referral r where r.inviter = :user or r.invitee = :user", user);
		bulk("delete from InviteCode c where c.user = :user", user);
		bulk("delete from Enquiry e where e.tenant = :user", user);
		em.createQuery("delete from IdempotencyRecord r where r.scope = :scope")
			.setParameter("scope", user.getPublicId().toString()).executeUpdate();
		
		// Homes: owner contact the user typed for others goes; homes they own stay, with the owner detached.
		bulk("update Property p set p.ownerName = null, p.ownerEmail = null, p.ownerPhone = null where p.addedBy = :user",
			user);
		em.createQuery("update Property p set p.landlord = :unclaimed where p.landlord = :user")
			.setParameter("unclaimed", unclaimed.get()).setParameter("user", user).executeUpdate();
		
		// Tenancies stay; requests nobody can confirm any more are cancelled, typed invite emails go.
		bulk("delete from InviteToken i where i.tenancy.id in"
			+ " (select t.id from Tenancy t where t.landlord = :user or t.tenant = :user)", user);
		em.createQuery("update Tenancy t set t.status = :cancelled, t.updatedAt = :now"
				+ " where t.status = :pending and (t.landlord = :user or t.tenant = :user)")
			.setParameter("cancelled", TenancyStatus.CANCELLED).setParameter("pending", TenancyStatus.PENDING_CONFIRMATION)
			.setParameter("now", now).setParameter("user", user).executeUpdate();
		bulk("update Tenancy t set t.inviteEmail = null where t.landlord = :user or t.tenant = :user", user);
		
		// The account row stays for the records that name it, with nothing personal left.
		user.setEmail(null);
		user.setEmailVerifiedAt(null);
		user.setGoogleSub(null);
		user.setGoogleName(null);
		user.setAvatarUrl(null);
		user.setPhone(null);
		user.setHomeCity(null);
		user.setLastLoginAt(null);
		user.setDisplayName(PublicNames.DELETED_USER);
		user.getRoles().clear();
		user.setStatus(UserStatus.DELETED);
		user.setDeletedAt(now);
		users.save(user);
		
		audit.record(user, ModerationActionType.ACCOUNT_DELETED, ModerationTargetType.USER, user.getId(),
			user.getPublicId().toString(), null);
		events.publishEvent(new AccountErased(user.getId(), user.getPublicId()));
		log.info("Account {} deleted", user.getPublicId());
	}
	
	private void bulk(String jpql, User user) {
		em.createQuery(jpql).setParameter("user", user).executeUpdate();
	}
	
}
