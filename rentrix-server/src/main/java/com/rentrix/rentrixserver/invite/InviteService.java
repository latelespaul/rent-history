package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.common.error.ApiException;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import com.rentrix.rentrixserver.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Invite friends (design note {@code invite-friends.md}): each user's random share code (IF-3) and the sign-ups it
 * brought (IF-5). The code resolves to a person only inside {@link #attribute}, which runs in the sign-up
 * transaction and answers nothing (IF-6, IF-7).
 */
@Service
@RequiredArgsConstructor
public class InviteService {
	
	/** Crockford base32: no {@code I L O U}, so a code read aloud or typed is not misread. */
	static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	static final int CODE_LENGTH = 10;
	private static final Pattern CODE = Pattern.compile("[0-9A-HJKMNP-TV-Z]{" + CODE_LENGTH + "}");
	private static final int MAX_DRAWS = 5;
	
	private final UserRepository users;
	private final InviteCodeRepository codes;
	private final ReferralRepository referrals;
	private final SecureRandom random = new SecureRandom();
	
	/** {@code GET /me/invite}: the caller's own code and how many people joined with it; never who. */
	public record InviteView(String code, long joined) {
	}
	
	/** The caller's code, made on the first call (the user row is locked, so two first calls make one code). */
	@Transactional
	public InviteView mine(UUID userPublicId) {
		User user = users.lockByPublicId(userPublicId)
			.filter(u -> u.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> ApiException.notFound("User"));
		InviteCode code = codes.findByUser(user).orElseGet(() -> create(user));
		return new InviteView(code.getCode(), referrals.countByInviter(user));
	}
	
	/**
	 * Records that {@code invitee}, created in this very sign-in, came through {@code ref} (IF-5). Ignored when the
	 * code is malformed or unknown, its owner is not active or is the invitee, or the invitee already counts for
	 * someone. Callers pass only a user this request created, so a sign-in of an existing user never counts.
	 */
	@Transactional
	public void attribute(User invitee, String ref) {
		Optional<String> code = normalise(ref);
		if (code.isEmpty() || referrals.existsByInvitee(invitee)) {
			return;
		}
		codes.findByCode(code.get()).map(InviteCode::getUser)
			.filter(inviter -> inviter.getStatus() == UserStatus.ACTIVE)
			.filter(inviter -> !inviter.getId().equals(invitee.getId()))
			.ifPresent(inviter -> {
				Referral referral = new Referral();
				referral.setInviter(inviter);
				referral.setInvitee(invitee);
				referrals.save(referral);
			});
	}
	
	/** A well-formed code, upper-cased, or empty. */
	static Optional<String> normalise(String ref) {
		if (ref == null) {
			return Optional.empty();
		}
		String code = ref.trim().toUpperCase(Locale.ROOT);
		return CODE.matcher(code).matches() ? Optional.of(code) : Optional.empty();
	}
	
	private InviteCode create(User user) {
		for (int i = 0; i < MAX_DRAWS; i++) {
			String candidate = draw();
			if (!codes.existsByCode(candidate)) {
				InviteCode code = new InviteCode();
				code.setUser(user);
				code.setCode(candidate);
				return codes.save(code);
			}
		}
		throw new IllegalStateException("No free invite code after " + MAX_DRAWS + " draws");
	}
	
	String draw() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}
	
}
