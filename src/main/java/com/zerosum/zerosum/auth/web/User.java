package com.zerosum.zerosum.auth.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * A registered user. The table is {@code users} because {@code user} is a reserved word in PostgreSQL.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Always lowercase; see {@link #normalizeEmail(String)}. */
	@Column(nullable = false)
	private String email;

	/** The encoded hash from the {@code PasswordEncoder}, never the raw password. */
	@Column(nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public User(String email, String passwordHash, Role role) {
		this.email = normalizeEmail(email);
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		this.role = Objects.requireNonNull(role, "role must not be null");
	}

	/**
	 * The form emails are stored and looked up in: trimmed and lowercase.
	 * Callers must normalize an email with this before passing it to {@code UserRepository}.
	 */
	public static String normalizeEmail(String email) {
		return Objects.requireNonNull(email, "email must not be null").trim().toLowerCase(Locale.ROOT);
	}

}
