package com.google.mu.spanner;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.testcontainers.containers.SpannerEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.InstanceAdminClient;
import com.google.cloud.spanner.InstanceConfigId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.InstanceInfo;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;

@RunWith(JUnit4.class)
@Ignore("Run manually with Docker image")
public class ParameterizedQuerySpannerTest {

  @ClassRule
  public static SpannerEmulatorContainer spannerEmulator = new SpannerEmulatorContainer(
      DockerImageName.parse("gcr.io/cloud-spanner-emulator/emulator:latest"));

  private static Spanner spanner;
  private static DatabaseClient dbClient;
  private static final String PROJECT_ID = "test-project";
  private static final String INSTANCE_ID = "test-instance";
  private static final String DATABASE_ID = "test-database";

  @BeforeClass
  public static void setup() throws Exception {
    // Testcontainers will automatically start the container when the test class is
    // initialized.
    // The container's network details will be available.

    // Set the environment variable so the Spanner client library connects to the
    // emulator
    System.setProperty("SPANNER_EMULATOR_HOST", spannerEmulator.getEmulatorGrpcEndpoint());

    SpannerOptions options = SpannerOptions.newBuilder()
        .setProjectId(PROJECT_ID)
        .setEmulatorHost(spannerEmulator.getEmulatorGrpcEndpoint()) // Use setEmulatorHost for direct emulator
                                                                    // connection
        .build();

    spanner = options.getService();

    // Create a test instance (emulator only has one, but this simulates real Spanner)
    // The emulator automatically creates a default instance if you try to create a
    // database within a non-existent instance, but explicitly creating it can be clearer.
    InstanceAdminClient instanceAdminClient = spanner.getInstanceAdminClient();
    InstanceId instanceId = InstanceId.of(PROJECT_ID, INSTANCE_ID);
    try {
      instanceAdminClient.createInstance(
          InstanceInfo.newBuilder(instanceId)
              .setInstanceConfigId(InstanceConfigId.of(PROJECT_ID, "emulator-config"))
              .build())
        .get();
    } catch (Exception e) {
      // Ignore if instance already exists (e.g., if multiple test classes run)
      if (!e.getMessage().contains("ALREADY_EXISTS")) {
        throw e;
      }
    }

    List<String> statements = asList(
        "CREATE TABLE Users (UserId STRING(36) NOT NULL, Name STRING(MAX), Email STRING(MAX)) PRIMARY KEY (UserId)",
        "CREATE TABLE Products (ProductId STRING(36) NOT NULL, Name STRING(MAX), Price INT64) PRIMARY KEY (ProductId)");

    // Create the database
    DatabaseAdminClient dbAdminClient = spanner.getDatabaseAdminClient();
    DatabaseId dbId = DatabaseId.of(PROJECT_ID, INSTANCE_ID, DATABASE_ID);

    try {
      dbAdminClient.createDatabase(INSTANCE_ID, DATABASE_ID, statements).get();
    } catch (Exception e) {
      // If the database somehow already exists, try dropping and recreating for
      // hermeticity
      if (e.getMessage().contains("ALREADY_EXISTS")) {
        System.out.println("Database already exists, attempting to drop and recreate...");
        dbAdminClient.dropDatabase(INSTANCE_ID, DATABASE_ID);
        dbAdminClient.createDatabase(INSTANCE_ID, DATABASE_ID, statements).get();
      } else {
        throw e;
      }
    }

    dbClient = spanner.getDatabaseClient(dbId);
    assertNotNull("Database client should not be null", dbClient);
  }

  @AfterClass
  public static void cleanup() {
    if (spanner != null) {
      // In a real scenario, you might drop the test database here if using
      // per-test-class database
      // databaseClient.close(); // Not strictly needed, spanner.close() will clean up
      spanner.close();
    }
    // Testcontainers will automatically stop the container when the JVM exits or
    // after all tests are run due to @ClassRule.
    // No explicit spannerEmulator.stop() is needed when using @Rule/@ClassRule.

    // Clean up system property
    System.clearProperty("SPANNER_EMULATOR_HOST");
  }

  @Test
  public void withStringParameters() {
    // Insert a user
    dbClient.readWriteTransaction().run(txn -> {
      Mutation mutation = Mutation.newInsertBuilder("Users")
          .set("UserId").to("user-123")
          .set("Name").to("Alice")
          .set("Email").to("alice@example.com")
          .build();
      txn.buffer(mutation);
      return null;
    });

    ParameterizedQuery query = ParameterizedQuery.of(
        "SELECT UserId, Name, Email FROM Users WHERE UserId = '{user}'", "user-123");
    // Read the user
    try (ResultSet resultSet = execute(query)) {
      assertEquals(true, resultSet.next()); // Should find a row
      assertEquals("user-123", resultSet.getString("UserId"));
      assertEquals("Alice", resultSet.getString("Name"));
      assertEquals("alice@example.com", resultSet.getString("Email"));
      assertEquals(false, resultSet.next()); // No more rows
    }
  }

  @Test
  public void stringLike() {
    // Insert a user
    dbClient.readWriteTransaction().run(txn -> {
      Mutation mutation = Mutation.newInsertBuilder("Users")
          .set("UserId").to("user-4\\56")
          .set("Name").to("Alice")
          .set("Email").to("alice@example.com")
          .build();
      txn.buffer(mutation);
      return null;
    });

    ParameterizedQuery query = ParameterizedQuery.of(
        "SELECT UserId, Name, Email FROM Users WHERE UserId LIKE '{searchTerm}'",
        "%er-%");
    // Read the user
    try (ResultSet resultSet = execute(query)) {
      assertEquals(true, resultSet.next()); // Should find a row
      assertEquals("user-4\\56", resultSet.getString("UserId"));
      assertEquals("Alice", resultSet.getString("Name"));
      assertEquals("alice@example.com", resultSet.getString("Email"));
    }
  }

  @Test
  public void withArrayParameter() {
    // Insert a user
    dbClient.readWriteTransaction().run(txn -> {
      Mutation mutation = Mutation.newInsertBuilder("Users")
          .set("UserId").to("user-7")
          .set("Name").to("Alice")
          .set("Email").to("alice@example.com")
          .build();
      txn.buffer(mutation);
      return null;
    });
    // Read the user
    ParameterizedQuery query = ParameterizedQuery.of(
        "SELECT UserId, Name, Email FROM Users WHERE UserId IN UNNEST('{ids}')",
        /* ids */ asList("user-6", "user-7"));
    try (ResultSet resultSet = execute(query)) {
      assertEquals(true, resultSet.next()); // Should find a row
      assertEquals("user-7", resultSet.getString("UserId"));
      assertEquals("Alice", resultSet.getString("Name"));
      assertEquals("alice@example.com", resultSet.getString("Email"));
      assertEquals(false, resultSet.next()); // No more rows
    }
  }

  @Test
  public void identifierParameterized() {
    // Insert a product
    dbClient.readWriteTransaction().run(txn -> {
      Mutation mutation = Mutation.newInsertBuilder("Products")
          .set("ProductId").to("prod-abc")
          .set("Name").to("Test Product")
          .set("Price").to(999L)
          .build();
      txn.buffer(mutation);
      return null;
    });

    ParameterizedQuery query = ParameterizedQuery.of("SELECT COUNT(*) FROM `{table}`", "Products");
    // Verify insertion (count all products)
    try (ResultSet resultSet = execute(query)) {
      assertEquals(true, resultSet.next());
      assertEquals(1, resultSet.getLong(0));
    }
  }

  private ResultSet execute(ParameterizedQuery query) {
    return dbClient.singleUse().executeQuery(query.statement());
  }
}
