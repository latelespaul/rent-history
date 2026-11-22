package com.rentrix.rentrixserver.property;

import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one system account that holds homes a tenant added while their landlord is not on the platform (D-138). It
 * never signs in (no Google subject, a reserved {@code .invalid} email), is never listed or emailed, and shows as
 * {@value #PUBLIC_NAME} wherever a landlord's name would. Keeping it a real row keeps every {@code landlord_id}
 * {@code NOT NULL}; a real landlord claims a home later by taking it over (an admin action).
 */
@Component
@RequiredArgsConstructor
public class UnclaimedLandlord {

	/** Reserved address: {@code .invalid} never resolves (RFC 2606), so nothing can be sent to it. */
	public static final String EMAIL = "unclaimed-homes@doorwayliving.invalid";

	/** What the web shows for the landlord of an unclaimed home. */
	public static final String PUBLIC_NAME = "Not on Doorway Living yet";

	private final UserRepository users;

	/** Whether {@code user} is the unclaimed-homes account. */
	public static boolean is(User user) {
		return user != null && EMAIL.equals(user.getEmail());
	}

	/** The account, created on first use. */
	@Transactional
	public User get() {
		return users.findByEmail(EMAIL).orElseGet(() -> {
			User user = new User();
			user.setEmail(EMAIL);
			user.setDisplayName("Unclaimed homes");
			user.getRoles().add(Role.LANDLORD);
			return users.saveAndFlush(user);
		});
	}

	/** Creates the row at start-up, so two first adds never race to insert it. */
	@EventListener(ApplicationReadyEvent.class)
	public void ensure() {
		get();
	}

}
