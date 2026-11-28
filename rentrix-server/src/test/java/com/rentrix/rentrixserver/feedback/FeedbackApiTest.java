package com.rentrix.rentrixserver.feedback;

import com.rentrix.rentrixserver.config.BrandProperties;
import com.rentrix.rentrixserver.notification.email.EmailMessage;
import com.rentrix.rentrixserver.notification.email.EmailSender;
import com.rentrix.rentrixserver.security.AccessTokenService;
import com.rentrix.rentrixserver.support.IntegrationTest;
import com.rentrix.rentrixserver.support.RecordingEmailSender;
import com.rentrix.rentrixserver.support.TestData;
import com.rentrix.rentrixserver.user.Role;
import com.rentrix.rentrixserver.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.rentrix.rentrixserver.support.AdminConsole.adminBearer;
import static com.rentrix.rentrixserver.support.AdminConsole.adminHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Contact us" form: stored, emailed to the team (best effort), honeypot, limits and the admin list. */
@IntegrationTest
@Transactional
class FeedbackApiTest {
	
	static final String TEAM = "hello@doorwayliving.in";
	
	@Autowired
	MockMvc mvc;
	
	@Autowired
	FeedbackRepository feedback;
	
	@Autowired
	RecordingEmailSender mail;
	
	@Autowired
	TestData data;
	
	@Autowired
	AccessTokenService tokens;
	
	private ResultActions send(String ip, String json) throws Exception {
		return mvc.perform(post("/feedback").with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content(json));
	}
	
	@Test
	void storesTheMessageAndEmailsTheTeam() throws Exception {
		long before = feedback.count();
		int mails = mail.to(TEAM).size();
		send("10.20.0.1", """
			{"name":"  Asha  ","email":"asha@example.test","type":"BUG","message":"  The search\\nbutton is stuck.  "}""")
			.andExpect(status().isNoContent());
		assertThat(feedback.count()).isEqualTo(before + 1);
		Feedback row = feedback.findAll().stream().filter(f -> "Asha".equals(f.getName())).findFirst().orElseThrow();
		assertThat(row.getEmail()).isEqualTo("asha@example.test");
		assertThat(row.getType()).isEqualTo(FeedbackType.BUG);
		assertThat(row.getMessage()).isEqualTo("The search\nbutton is stuck.");
		assertThat(mail.to(TEAM)).hasSize(mails + 1);
		EmailMessage sent = mail.last(TEAM);
		assertThat(sent.subject()).contains("bug").contains("The search button is stuck.");
		assertThat(sent.body()).contains("Asha").contains("Reply to: asha@example.test");
	}
	
	@Test
	void nameAndEmailAreOptional() throws Exception {
		long before = feedback.count();
		send("10.20.0.2", """
			{"name":"","email":"","type":"IDEA","message":"Add Mumbai"}""").andExpect(status().isNoContent());
		assertThat(feedback.count()).isEqualTo(before + 1);
		assertThat(mail.last(TEAM).body()).contains("from someone").contains("no reply is possible");
	}
	
	@Test
	void aFilledHoneypotLooksFineButKeepsAndSendsNothing() throws Exception {
		long before = feedback.count();
		int mails = mail.to(TEAM).size();
		send("10.20.0.3", """
			{"type":"FEEDBACK","message":"Buy cheap pills","website":"http://spam.example"}""")
			.andExpect(status().isNoContent());
		assertThat(feedback.count()).isEqualTo(before);
		assertThat(mail.to(TEAM)).hasSize(mails);
	}
	
	@Test
	void rejectsBadInput() throws Exception {
		send("10.20.0.4", "{\"type\":\"FEEDBACK\",\"message\":\"   \"}").andExpect(status().isUnprocessableContent());
		send("10.20.0.4", "{\"message\":\"hi\"}").andExpect(status().isUnprocessableContent());
		send("10.20.0.4", "{\"type\":\"FEEDBACK\",\"message\":\"hi\",\"email\":\"not-an-email\"}")
			.andExpect(status().isUnprocessableContent());
		send("10.20.0.4", "{\"type\":\"FEEDBACK\",\"message\":\"" + "a".repeat(FeedbackService.MESSAGE_MAX + 1) + "\"}")
			.andExpect(status().isUnprocessableContent());
	}
	
	@Test
	void rateLimitsOneAddress() throws Exception {
		String body = "{\"type\":\"OTHER\",\"message\":\"hello\"}";
		for (int i = 0; i < FeedbackRateLimiter.REQUESTS; i++) {
			send("10.20.9.9", body).andExpect(status().isNoContent());
		}
		send("10.20.9.9", body).andExpect(status().isTooManyRequests());
	}
	
	@Test
	void aSignedInUserMidOnboardingCanSendToo() throws Exception {
		User fresh = data.user("Fresh " + UUID.randomUUID());
		mvc.perform(post("/feedback").with(request -> {
				request.setRemoteAddr("10.20.0.5");
				return request;
			}).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(fresh.getPublicId(), java.util.List.of()))
			.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"OTHER\",\"message\":\"hi\"}"))
			.andExpect(status().isNoContent());
	}
	
	@Test
	void adminListNeedsAnAdminAndIsNewestFirst() throws Exception {
		mvc.perform(get("/admin/feedback").with(adminHost())).andExpect(status().isUnauthorized());
		send("10.20.0.6", "{\"type\":\"FEEDBACK\",\"message\":\"First note\"}").andExpect(status().isNoContent());
		send("10.20.0.6", "{\"name\":\"Ravi\",\"type\":\"IDEA\",\"message\":\"Second note\"}")
			.andExpect(status().isNoContent());
		User admin = data.user("Feedback Admin " + UUID.randomUUID(), Role.ADMIN);
		mvc.perform(get("/admin/feedback").with(adminHost())
				.header(HttpHeaders.AUTHORIZATION, adminBearer(tokens, admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].message").value("Second note"))
			.andExpect(jsonPath("$.items[0].name").value("Ravi"))
			.andExpect(jsonPath("$.items[0].type").value("IDEA"))
			.andExpect(jsonPath("$.items[1].message").value("First note"))
			.andExpect(jsonPath("$.page.number").value(0));
	}
	
	@Test
	void aFailingMailerNeverFailsTheMessage() {
		FeedbackRepository repository = mock(FeedbackRepository.class);
		when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
		EmailSender broken = message -> {
			throw new IllegalStateException("provider down");
		};
		FeedbackService service = new FeedbackService(repository, broken, new BrandProperties("Doorway Living"), TEAM);
		service.receive(null, null, FeedbackType.BUG, "It broke");
		verify(repository).save(any());
	}
	
}
