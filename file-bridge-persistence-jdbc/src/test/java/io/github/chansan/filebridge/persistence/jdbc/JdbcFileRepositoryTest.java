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
  private JdbcIdempotencyRepository idempotencyRepository;
  private JdbcReconciliationRepository reconciliationRepository;
  private JdbcUploadRepository uploadRepository;
  private NamedParameterJdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/filebridge/migration")
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/filebridge/migration")
        .load()
        .migrate();
    jdbc = new NamedParameterJdbcTemplate(ds);
    repository = new JdbcFileRepository(jdbc);
    idempotencyRepository = new JdbcIdempotencyRepository(jdbc);
    reconciliationRepository = new JdbcReconciliationRepository(jdbc);
    uploadRepository = new JdbcUploadRepository(jdbc);
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

  @Test
  void keepsFirstIdempotentResponseForConcurrentEquivalentRequest() {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    String first =
        idempotencyRepository.save(
            "tenant", "owner", "UPLOAD_INIT", "key", "hash", "UPLOAD:first", expiresAt);
    String second =
        idempotencyRepository.save(
            "tenant", "owner", "UPLOAD_INIT", "key", "hash", "UPLOAD:second", expiresAt);

    assertThat(first).isEqualTo("UPLOAD:first");
    assertThat(second).isEqualTo("UPLOAD:first");
  }

  @Test
  void replacesExpiredIdempotencyRecord() {
    Instant expiresAt = Instant.now().plusSeconds(3600);
    idempotencyRepository.save(
        "tenant", "owner", "UPLOAD_INIT", "expired-key", "old-hash", "UPLOAD:old", expiresAt);
    jdbc.update(
        "UPDATE fb_idempotency_record SET expires_at=DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND) "
            + "WHERE idempotency_key='expired-key'",
        java.util.Map.of());

    String response =
        idempotencyRepository.save(
            "tenant", "owner", "UPLOAD_INIT", "expired-key", "new-hash", "UPLOAD:new", expiresAt);

    assertThat(response).isEqualTo("UPLOAD:new");
  }

  @Test
  void startsRetentionWhenLastReferenceIsDeleted() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID objectId = UUID.randomUUID();
    repository.insertObject(object(objectId, now));
    FileReference reference = reference(objectId, now);
    repository.insertReference(reference);

    assertThat(repository.markReferenceDeleted(reference.id(), now)).isTrue();
    assertThat(repository.findUnreferencedAvailable(now.minusSeconds(1), 10)).isEmpty();
    assertThat(repository.findUnreferencedAvailable(now.plusSeconds(1), 10))
        .extracting(StorageObjectRecord::id)
        .contains(objectId);
  }

  @Test
  void refusesNewReferenceAfterDeleteHasBeenClaimed() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID objectId = UUID.randomUUID();
    repository.insertObject(object(objectId, now));
    assertThat(repository.markObjectDeletePending(objectId, now)).isTrue();

    assertThatThrownBy(() -> repository.insertReference(reference(objectId, now)))
        .isInstanceOf(io.github.chansan.filebridge.core.error.FileBridgeException.class);
  }

  @Test
  void persistsAndRetriesReconciliationIssue() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    ReconciliationIssue issue =
        reconciliationRepository.upsert(
            "OBJECT_DELETE_FAILED:1",
            "OBJECT_DELETE_FAILED",
            "local-main",
            null,
            "object-key",
            null,
            UUID.randomUUID().toString(),
            "temporary failure",
            now,
            now);

    assertThat(reconciliationRepository.findDue(now.plusSeconds(1), 10))
        .extracting(ReconciliationIssue::id)
        .contains(issue.id());
    assertThat(reconciliationRepository.acquire(issue.id(), "worker", now.plusSeconds(60), now))
        .isTrue();
    reconciliationRepository.retry(issue.id(), "worker", "retry", now.plusSeconds(30), 3, now);
    assertThat(reconciliationRepository.findDue(now.plusSeconds(31), 10))
        .extracting(ReconciliationIssue::id)
        .contains(issue.id());
  }

  @Test
  void coordinatesCompletionRequestLeaseAndRetry() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID uploadId = UUID.randomUUID();
    uploadRepository.insertTask(
        new UploadTask(
            uploadId,
            "tenant",
            "owner",
            "local-main",
            "object-key",
            "file.bin",
            null,
            null,
            "application/octet-stream",
            10,
            null,
            5,
            2,
            UploadTaskStatus.UPLOADING,
            "provider-upload",
            null,
            now.plusSeconds(3600),
            null,
            null,
            0,
            now,
            now));

    assertThat(uploadRepository.requestCompletion(uploadId, now)).isTrue();
    assertThat(
            uploadRepository.acquireCompletionLease(uploadId, "worker-a", now.plusSeconds(60), now))
        .isTrue();
    assertThat(
            uploadRepository.acquireCompletionLease(uploadId, "worker-b", now.plusSeconds(60), now))
        .isFalse();
    assertThat(
            uploadRepository.renewCompletionLease(
                uploadId, "worker-a", now.plusSeconds(120), now.plusSeconds(1)))
        .isTrue();
    uploadRepository.retryCompletion(
        uploadId, "worker-a", "temporary", now.plusSeconds(30), 3, now.plusSeconds(2));
    assertThat(uploadRepository.findCompletableOrExpiredLeases(now.plusSeconds(10), 10)).isEmpty();
    assertThat(uploadRepository.findCompletableOrExpiredLeases(now.plusSeconds(31), 10))
        .extracting(UploadTask::id)
        .contains(uploadId);
  }

  private static StorageObjectRecord object(UUID objectId, Instant now) {
    return new StorageObjectRecord(
        objectId,
        new ObjectLocation("local-main", null, "2026/09/" + objectId),
        5,
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        "text/plain",
        StorageObjectStatus.AVAILABLE,
        now,
        now,
        now,
        0);
  }

  private static FileReference reference(UUID objectId, Instant now) {
    return new FileReference(
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
  }
}
