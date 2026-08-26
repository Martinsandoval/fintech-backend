package com.example.fintech.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the configured Postgres database on startup if it doesn't exist yet.
 * Postgres has no CREATE DATABASE IF NOT EXISTS, so this connects to the
 * "postgres" maintenance database first to check pg_database. Runs on
 * ApplicationEnvironmentPreparedEvent so it happens before Hikari/Flyway
 * try to open a connection to the (possibly missing) target database.
 */
public class DatabaseInitializer implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		ConfigurableEnvironment environment = event.getEnvironment();
		String url = environment.getProperty("spring.datasource.url");
		String user = environment.getProperty("spring.datasource.username");
		String password = environment.getProperty("spring.datasource.password", "");

		if (url == null || !url.startsWith("jdbc:postgresql://")) {
			return;
		}

		URI uri = URI.create(url.substring("jdbc:".length()));
		String database = uri.getPath().replaceFirst("^/", "");
		String adminUrl = "jdbc:postgresql://" + uri.getAuthority() + "/postgres";

		try (Connection connection = DriverManager.getConnection(adminUrl, user, password)) {
			if (!databaseExists(connection, database)) {
				try (Statement statement = connection.createStatement()) {
					statement.executeUpdate("CREATE DATABASE \"" + database + "\"");
					System.out.println("[DatabaseInitializer] Created database '" + database + "'");
				}
			}
		} catch (SQLException e) {
			System.err.println("[DatabaseInitializer] Could not verify/create database '"
					+ database + "': " + e.getMessage());
		}
	}

	private boolean databaseExists(Connection connection, String database) throws SQLException {
		try (PreparedStatement statement =
				connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
			statement.setString(1, database);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next();
			}
		}
	}
}
