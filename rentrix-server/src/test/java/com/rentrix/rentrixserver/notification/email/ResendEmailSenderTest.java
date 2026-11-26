package com.rentrix.rentrixserver.notification.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No real Resend call is ever made: {@link ResendEmailSender} runs against a mock server bound to its client. */
@ExtendWith(OutputCaptureExtension.class)
class ResendEmailSenderTest {
	
	private static final String KEY = "re_test_key_not_real";
	private static final MailProperties ON = new MailProperties(true, KEY, null, "https://api.example.test/api/v1",
		Duration.ZERO, null);
	private static final EmailMessage REMINDER = new EmailMessage("asha.rao@example.test", "Rate your landlord",
		"Hi Asha, rate your landlord.", "<p>Hi Asha</p>", "https://api.example.test/api/v1/mail/unsubscribe?token=t");
	private static final Executor NOW = Runnable::run;
	
	@AfterEach
	void clearSynchronization() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}
	
	@Test
	void postsOneEmailWithTheKeyTheSenderAndTheUnsubscribeHeaders(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + KEY))
			.andExpect(content().json("""
				{"from":"Doorway Living <hello@doorwayliving.in>","to":["asha.rao@example.test"],
				 "subject":"Rate your landlord","text":"Hi Asha, rate your landlord.","html":"<p>Hi Asha</p>",
				 "headers":{"List-Unsubscribe":"<https://api.example.test/api/v1/mail/unsubscribe?token=t>",
				            "List-Unsubscribe-Post":"List-Unsubscribe=One-Click"}}""", true))
			.andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));
		new ResendEmailSender(builder, ON, NOW).sendNow(REMINDER);
		server.verify();
		assertThat(output.getAll()).doesNotContain(KEY).doesNotContain("asha.rao").doesNotContain("Hi Asha");
	}
	
	@Test
	void aTransactionalEmailHasNoUnsubscribeHeader() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(content().json("""
				{"to":["ravi@example.test"],"subject":"Tenancy confirmed","text":"Both sides confirmed."}"""))
			.andExpect(jsonPath("$.headers").doesNotExist())
			.andExpect(jsonPath("$.html").doesNotExist())
			.andRespond(withSuccess());
		new ResendEmailSender(builder, ON, NOW)
			.sendNow(new EmailMessage("ravi@example.test", "Tenancy confirmed", "Both sides confirmed."));
		server.verify();
	}
	
	@Test
	void aRefusalKeepsTheRecipientTextAndKeyOutOfTheError() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.resend.com/emails"))
			.andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
				.body("asha.rao@example.test Hi Asha " + KEY));
		ResendEmailSender sender = new ResendEmailSender(builder, ON, NOW);
		assertThatThrownBy(() -> sender.sendNow(REMINDER))
			.isInstanceOf(EmailSendException.class)
			.hasMessageContaining("429")
			.satisfies(e -> assertThat(e.getMessage()).doesNotContain("asha").doesNotContain("Hi Asha")
				.doesNotContain(KEY));
	}
	
	@Test
	void sendWaitsForTheCommitAndOnlyLogsAFailure(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(
			org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));
		List<Runnable> queued = new ArrayList<>();
		ResendEmailSender sender = new ResendEmailSender(builder, ON, queued::add);
		
		TransactionSynchronizationManager.initSynchronization();
		sender.send(REMINDER);
		assertThat(queued).isEmpty();
		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		assertThat(queued).hasSize(1);
		
		queued.getFirst().run();
		server.verify();
		assertThat(output.getAll()).contains("Email to a***@example.test not sent", "HTTP 500")
			.doesNotContain("asha.rao");
	}
	
	@Test
	void mailIsOffWithoutTheKeyTheSwitchOrTheWebUrl(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder();
		String web = "https://doorwayliving.in";
		assertThat(MailConfig.choose(new MailProperties(true, null, null, "https://api.example.test", null, null),
			web, builder, NOW)).isInstanceOf(LogEmailSender.class);
		assertThat(output.getAll()).contains("Mail is off (no RESEND_API_KEY)");
		assertThat(MailConfig.choose(new MailProperties(false, KEY, null, "https://api.example.test", null, null),
			web, builder, NOW)).isInstanceOf(LogEmailSender.class);
		assertThat(MailConfig.choose(new MailProperties(true, KEY, null, null, null, null), web, builder, NOW))
			.isInstanceOf(ResendEmailSender.class);
		assertThat(MailConfig.choose(new MailProperties(true, KEY, null, "https://api.example.test", null, null),
			"", builder, NOW)).isInstanceOf(LogEmailSender.class);
		EmailSender on = MailConfig.choose(ON, web, builder, NOW);
		assertThat(on).isInstanceOf(ResendEmailSender.class);
		assertThat(on.delivers()).isTrue();
		assertThat(new LogEmailSender().delivers()).isFalse();
		assertThat(output.getAll()).contains("Mail is on: Resend").doesNotContain(KEY);
	}
	
	@Test
	void defaultsAndTheKeyNeverPrinted() {
		MailProperties defaults = new MailProperties(null, " ", null, "https://api.example.test/api/v1/", null, null);
		assertThat(defaults.enabled()).isTrue();
		assertThat(defaults.resendApiKey()).isNull();
		assertThat(defaults.from()).isEqualTo("Doorway Living <hello@doorwayliving.in>");
		assertThat(defaults.apiBaseUrl()).isEqualTo("https://api.example.test/api/v1");
		assertThat(defaults.sendInterval()).isEqualTo(Duration.ofMillis(600));
		assertThat(ON.toString()).doesNotContain(KEY);
	}
	
}
