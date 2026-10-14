package com.zerosum.zerosum.auth.service;

import com.zerosum.zerosum.auth.exception.EmailAlreadyRegisteredException;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.common.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class AuthServiceEventTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ApplicationEvents events;

	@BeforeEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void publishesUserRegisteredEventWithTheNewUsersId() {
		User user = authService.register(new RegisterRequest("dave@example.com", "s3cret-Passw0rd"));

		assertThat(events.stream(UserRegisteredEvent.class))
				.containsExactly(new UserRegisteredEvent(user.getId()));
	}

	@Test
	void publishesNoEventWhenEmailIsAlreadyRegistered() {
		authService.register(new RegisterRequest("erin@example.com", "s3cret-Passw0rd"));
		events.clear();

		assertThatThrownBy(() -> authService.register(new RegisterRequest("Erin@Example.com", "s3cret-Passw0rd")))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
		assertThat(events.stream(UserRegisteredEvent.class)).isEmpty();
	}

}
