package com.malt.mongopostgresqlstreamer.configurations;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.postgresql.jdbc.PgConnection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.datasource.platform", havingValue = "postgresql")
public class PostgreSqlConfiguration {
	@Bean
	public BaseConnection baseConnection(DataSource dataSource) throws SQLException {
		return dataSource.getConnection()
				.unwrap(PgConnection.class);
	}

	@Bean
	public CopyManager copyManager(BaseConnection baseConnection) throws SQLException {
		return new CopyManager(baseConnection);
	}
}
