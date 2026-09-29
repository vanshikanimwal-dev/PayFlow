package com.payflow.account;

import com.payflow.config.PayflowProperties;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Component
public class DbLockTimeout {

    private final EntityManager entityManager;
    private final PayflowProperties properties;

    public DbLockTimeout(EntityManager entityManager, PayflowProperties properties) {
        this.entityManager = entityManager;
        this.properties = properties;
    }

    public void apply() {
        String timeout = properties.getLocking().lockTimeoutSql();
        if (!timeout.matches("[0-9]+s")) {
            throw new IllegalStateException("Invalid lock timeout");
        }
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '" + timeout + "'");
            } catch (SQLException ex) {
                throw new IllegalStateException("Could not set lock_timeout", ex);
            }
        });
    }
}
