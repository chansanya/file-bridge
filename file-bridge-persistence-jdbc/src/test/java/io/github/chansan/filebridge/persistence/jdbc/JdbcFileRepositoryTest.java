package io.github.chansan.filebridge.persistence.jdbc;

import static org.assertj.core.api.Assertions.*;

import io.github.chansan.filebridge.core.model.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcFileRepositoryTest {
  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
  private JdbcFileRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    repository = new JdbcFileRepository(new NamedParameterJdbcTemplate(ds));
  }

  @Test
  void keepsPhysicalObjectSeparateFromBusinessReference() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID objectId = UUID.randomUUID();
    StorageObjectRecord object =
        new StorageObjectRecord(
            objectId,
            new ObjectLocation("local-main", null, "2026/09/object"),
            5,
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            "text/plain",
            StorageObjectStatus.AVAILABLE,
            now,
            now,
            now,
            0);
    repository.insertObject(object);
    FileReference ref =
        new FileReference(
            UUID.randomUUID(),
            objectId,
            "tenant",
            "owner",
            "hello.txt",
            null,
            null,
            FileReferenceStatus.ACTIVE,
            now,
            null);
    repository.insertReference(ref);
    assertThat(repository.findReference(ref.id())).contains(ref);
    assertThat(repository.countActiveReferences(objectId)).isEqualTo(1);
  }
}
