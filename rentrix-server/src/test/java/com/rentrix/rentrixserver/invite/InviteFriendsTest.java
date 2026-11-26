package com.rentrix.rentrixserver.invite;

import com.rentrix.rentrixserver.auth.google.FakeGoogleTokenVerifier;
import com.rentrix.rentrixserver.property.Property;
import com.rentrix.rentrixserver.property.PropertyStatus;
import com.rentrix.rentrixserver.property.PropertyTestSupport;
import com.rentrix.rentrixserver.review.Review;
import com.rentrix.rentrixserver.review.ReviewDirection;
import com.rentrix.rentrixserver.review.ReviewRepository;
import com.rentrix.rentrixserver.review.ReviewStatus;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.tenancy.Tenancy;
import com.rentrix.rentrixserver.tenancy.TenancyStatus;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import com.rentrix.rentrixserver.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.rentrix.rentrixserver.auth.AuthTestSupport.freshIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Invite friends (design note {@code invite-friends.md}): the code (IF-3), attribution once on sign-up only (IF-5),
 * deletion (IF-8) and that no public or other-user response carries a code or a count (IF-7). Shares the dev-login
 * context of {@code DevLoginTest}, so {@code POST /auth/dev-login/new} stands in for a first Google sign-in.
 */
@SpringBootTest(properties = {"app.dev-login.enabled=true", "app.admin-console.emails=console-admin@example.test,listed-demo@example.test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InviteFriendsTest {

	private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

	@Autowired
	MockMvc mvc;

	@Autowired
	ObjectMapper json;

	@Autowired
	TestData data;

	@Autowired
	PropertyTestSupport support;

	@Autowired
	UserRepository users;

	@Autowired
	ReviewRepository reviews;

	@Autowired
	InviteCodeRepository codes;

	@Autowired
	ReferralRepository referrals;

	/** An onboarded user, so the onboarding filter lets them call {@code /me/invite}. */
	private User member(String name, Role... roles) {
		User user = data.verified(name, roles);
		user.setOnboardedAt(Instant.parse("2025-01-01T00:00:00Z"));
		return users.save(user);
	}

	private JsonNode invite(String bearer) throws Exception {
		MockHttpServletResponse response = mvc.perform(get("/me/invite").header(HttpHeaders.AUTHORIZATION, bearer))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return json.readTree(response.getContentAsString());
	}

	private String code(User user) throws Exception {
		return invite(support.bearer(user)).get("code").asString();
	}

	private long joined(User user) throws Exception {
		return invite(support.bearer(user)).get("joined").asLong();
	}

	/** {@code POST /auth/dev-login/new}; {@code ref} may be null. Returns the response body. */
	private JsonNode signUp(String key, String ref) throws Exception {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("key", key);
		if (ref != null) {
			body.put("ref", ref);
		}
		MockHttpServletResponse response = mvc.perform(post("/auth/dev-login/new").with(freshIp())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return json.readTree(response.getContentAsString());
	}

	private JsonNode google(String sub, String email, String ref) throws Exception {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("idToken", FakeGoogleTokenVerifier.token(sub, email));
		if (ref != null) {
			body.put("ref", ref);
		}
		MockHttpServletResponse response = mvc.perform(post("/auth/google").with(freshIp())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andReturn().getResponse();
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return json.readTree(response.getContentAsString());
	}

	private static String key() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	private User byPublicId(JsonNode session) {
		return users.findByPublicId(UUID.fromString(session.at("/user/id").asString())).orElseThrow();
	}

	@Test
	void codeIsRandomStableAndPrivateToItsOwner() throws Exception {
		User asha = member("Asha Rao", Role.TENANT);
		User ravi = member("Ravi Kumar", Role.TENANT);
		JsonNode first = invite(support.bearer(asha));
		assertThat(first.get("code").asString()).matches("[0-9A-HJKMNP-TV-Z]{10}");
		assertThat(first.get("joined").asLong()).isZero();
		assertThat(code(asha)).isEqualTo(first.get("code").asString());
		assertThat(code(ravi)).isNotEqualTo(first.get("code").asString());
		assertThat(first.properties().stream().map(Map.Entry::getKey).toList()).containsExactly("code", "joined");
	}

	@Test
	void signUpThroughALinkCountsOnceForTheInviter() throws Exception {
		User asha = member("Asha Rao", Role.TENANT);
		String code = code(asha);
		String friendKey = key();
		JsonNode friend = signUp(friendKey, code.toLowerCase());
		assertThat(joined(asha)).isEqualTo(1);

		// The same account signing up again, or under another code, never counts twice.
		User other = member("Other Inviter", Role.TENANT);
		signUp(friendKey, code(other));
		assertThat(joined(asha)).isEqualTo(1);
		assertThat(joined(other)).isZero();
		assertThat(referrals.existsByInvitee(byPublicId(friend))).isTrue();
	}

	@Test
	void googleSignUpCountsAndAnExistingAccountSigningInDoesNot() throws Exception {
		User asha = member("Asha Rao", Role.TENANT);
		String code = code(asha);
		String sub = "sub-" + UUID.randomUUID();
		String email = "friend-" + UUID.randomUUID() + "@example.test";
		JsonNode created = google(sub, email, code);
		assertThat(joined(asha)).isEqualTo(1);

		JsonNode again = google(sub, email, code);
		assertThat(joined(asha)).isEqualTo(1);
		// The reply never says what happened to the code.
		assertThat(again.toString()).doesNotContain(code);
		assertThat(created.toString()).doesNotContain(code);

		// An existing member who opens a link and signs in is not a new friend.
		User existing = member("Already Here", Role.TENANT);
		existing.setGoogleSub("sub-" + UUID.randomUUID());
		users.save(existing);
		google(existing.getGoogleSub(), existing.getEmail(), code);
		assertThat(joined(asha)).isEqualTo(1);
	}

	@Test
	void unknownMalformedOrInactiveCodesAreIgnored() throws Exception {
		signUp(key(), "NOTACODE!!");
		signUp(key(), "0000000000");

		User gone = member("Suspended Inviter", Role.TENANT);
		String code = code(gone);
		gone.setStatus(com.rentrix.rentrixserver.user.UserStatus.SUSPENDED);
		users.save(gone);
		JsonNode friend = signUp(key(), code);
		assertThat(referrals.existsByInvitee(byPublicId(friend))).isFalse();
	}

	@Test
	void deletingAnAccountRemovesItsCodeAndReferrals() throws Exception {
		User asha = member("Asha Rao", Role.TENANT);
		String code = code(asha);
		JsonNode friend = signUp(key(), code);
		assertThat(joined(asha)).isEqualTo(1);

		// The friend deletes their account: they no longer count.
		MockHttpServletResponse deleted = mvc.perform(delete("/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + friend.get("accessToken").asString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
			.andReturn().getResponse();
		assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(204);
		assertThat(joined(asha)).isZero();

		// The inviter deletes theirs: the code goes and no longer counts anyone.
		JsonNode inviter = signUp(key(), null);
		User inviterUser = byPublicId(inviter);
		inviterUser.getRoles().add(Role.TENANT);
		inviterUser.setOnboardedAt(Instant.parse("2025-01-01T00:00:00Z"));
		users.save(inviterUser);
		String inviterCode = code(inviterUser);
		signUp(key(), inviterCode);
		MockHttpServletResponse gone = mvc.perform(delete("/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + inviter.get("accessToken").asString())
				.contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":\"DELETE\"}"))
			.andReturn().getResponse();
		assertThat(gone.getStatus()).as(gone.getContentAsString()).isEqualTo(204);
		assertThat(codes.findByCode(inviterCode)).isEmpty();
		JsonNode late = signUp(key(), inviterCode);
		assertThat(referrals.existsByInvitee(byPublicId(late))).isFalse();
	}

	/**
	 * IF-7: a tenant who reviewed a home invites a friend. No response anyone else can read (signed out, the
	 * reviewed landlord, the friend, a stranger), nor the inviter's own {@code GET /me}, holds the code or a count.
	 */
	@Test
	void noPublicOrOtherUserResponseCarriesTheCodeOrACount() throws Exception {
		User landlord = member("Lata Landlord", Role.LANDLORD);
		User inviter = member("Ishan Inviter", Role.TENANT);
		User stranger = member("Sara Stranger", Role.TENANT);
		Property home = data.property(landlord, "BLR", "Koramangala", PropertyStatus.PUBLISHED);
		Tenancy tenancy = data.tenancy(home, inviter, TODAY.minusYears(2), null, TenancyStatus.ACTIVE);
		Review review = new Review();
		review.setTenancy(tenancy);
		review.setDirection(ReviewDirection.TENANT_TO_PROPERTY);
		review.setAuthor(inviter);
		review.setProperty(home);
		review.setRatingOverall(4);
		review.setBody("Quiet street, the water supply was steady all year.");
		review.setStatus(ReviewStatus.PUBLISHED);
		review.setSubmittedAt(Instant.parse("2025-02-01T00:00:00Z"));
		review = reviews.save(review);

		String code = code(inviter);
		JsonNode friendSession = signUp(key(), code);
		assertThat(joined(inviter)).isEqualTo(1);
		String friend = "Bearer " + friendSession.get("accessToken").asString();

		String homeId = home.getPublicId().toString();
		String inviterId = inviter.getPublicId().toString();
		Map<String, String> anonymous = Map.of();
		Map<String, String> asLandlord = Map.of(HttpHeaders.AUTHORIZATION, support.bearer(landlord));
		Map<String, String> asStranger = Map.of(HttpHeaders.AUTHORIZATION, support.bearer(stranger));
		Map<String, String> asFriend = Map.of(HttpHeaders.AUTHORIZATION, friend);
		Map<String, String> asInviter = Map.of(HttpHeaders.AUTHORIZATION, support.bearer(inviter));

		// ok: the call must succeed, so the check reads a real body; the others may be refused (D-54, D-60).
		record Call(String path, Map<String, String> headers, boolean ok) {

			Call(String path, Map<String, String> headers) {
				this(path, headers, true);
			}

		}
		List<Call> calls = List.of(
			new Call("/properties/" + homeId, anonymous),
			new Call("/properties/" + homeId + "/reviews", anonymous),
			new Call("/properties/" + homeId + "/rating", anonymous),
			new Call("/properties?city=BLR", anonymous, false),
			new Call("/reviews/" + review.getPublicId(), anonymous, false),
			new Call("/properties/" + homeId, asLandlord),
			new Call("/properties/" + homeId + "/reviews", asLandlord),
			new Call("/reviews/" + review.getPublicId(), asLandlord),
			new Call("/users/" + inviterId, asLandlord, false),
			new Call("/users/" + inviterId + "/reviews?as=TENANT", asLandlord, false),
			new Call("/me/reviews/received?as=LANDLORD", asLandlord, false),
			new Call("/tenancies/" + tenancy.getPublicId(), asLandlord),
			new Call("/me/tenancies", asLandlord, false),
			new Call("/me/tenancies", asFriend, false),
			new Call("/users/" + inviterId, asStranger, false),
			new Call("/me", asStranger),
			new Call("/me", asFriend),
			new Call("/me", asInviter));
		for (Call call : calls) {
			var request = get(call.path());
			call.headers().forEach(request::header);
			MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
			String body = response.getContentAsString();
			if (call.ok()) {
				assertThat(response.getStatus()).as(call.path() + " " + body).isEqualTo(200);
			}
			assertThat(body).as(call.path() + " " + call.headers().keySet())
				.doesNotContain(code)
				.doesNotContainIgnoringCase("\"ref\"")
				.doesNotContainIgnoringCase("referral")
				.doesNotContainIgnoringCase("invitecode")
				.doesNotContain("\"joined\"");
		}
	}

}
