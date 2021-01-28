package com.malt.mongopostgresqlstreamer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.malt.mongopostgresqlstreamer.config.StreamerTestConfig;
import com.malt.mongopostgresqlstreamer.consumers.db.CheckpointManager;
import com.malt.mongopostgresqlstreamer.consumers.db.InitialImporter;
import com.malt.mongopostgresqlstreamer.consumers.db.OplogStreamer;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.awaitility.core.ConditionFactory;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;

@SpringBootTest(classes = StreamerTestConfig.class)
@ContextConfiguration(initializers = {StreamerIntegrationTest.Initializer.class})
@ActiveProfiles("it")
@ExtendWith(SpringExtension.class)
class StreamerIntegrationTest {

    private static final String DATA_FILE_NAME = "data-it.json";
    private static final String DATA_UPDATE_FILE_NAME = "data-update-it.json";

    private static final int MONGO_PORT = 27017;

    private static final PostgreSQLContainer postgreSQLContainer =
            (PostgreSQLContainer) new PostgreSQLContainer("postgres:10.4")
                    .withDatabaseName("streamer")
                    .withUsername("user")
                    .withPassword("password")
                    .withStartupTimeout(Duration.ofSeconds(600));

    private static final GenericContainer mongoContainer =
            new GenericContainer("mongo:3.2.16")
                    .withCommand("--replSet \"rs0\"")
                    .withExposedPorts(MONGO_PORT);

    @Autowired
    CheckpointManager checkpointManager;
    @Autowired
    InitialImporter initialImporter;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MongoClient mongoClient;
    @Autowired
    OplogStreamer oplogStreamer;

    Thread streamerThread;

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            EnvironmentTestUtils.addEnvironment("testcontainers", configurableApplicationContext.getEnvironment(),
                    "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                    "spring.datasource.username=" + postgreSQLContainer.getUsername(),
                    "spring.datasource.password=" + postgreSQLContainer.getPassword(),
                    "mongo.uri=mongodb://localhost:" + mongoContainer.getMappedPort(MONGO_PORT)
            );
        }
    }

    @BeforeAll
    static void setUp() throws IOException, InterruptedException {
        postgreSQLContainer.start();
        mongoContainer.start();
        mongoContainer.execInContainer("/usr/bin/mongo", "--eval", "rs.initiate()");
    }

    @AfterAll
    static void cleanUp() {
        postgreSQLContainer.stop();
        mongoContainer.stop();
    }

    @AfterEach
    void stopStreamerAndClearData() {
        if (streamerThread != null) {
            streamerThread.interrupt();
            streamerThread = null;
        }
        clearData();
    }

    @Test
    void should_import_data_from_mongo_to_pgsql() throws Exception {
        // when
        loadData();
        initialImporter.start();

        // then
        assertThatInitialRowCountsAreAsExpected();
    }

    @Test
    void should_propagate_updates_from_mongo_to_pgsql() throws Exception {
        // given that data are already present
        loadData();
        initialImporter.start();

        assertThatInitialRowCountsAreAsExpected();

        // when watching for changes and updating data
        launchStreamerFromLastOplog();
        updateData();  // remove some linked characters and ratings

        // then
        awaitSomeTime().untilAsserted(() -> {
            assertThat(countRowsInTable(jdbcTemplate, "superheros")).isEqualTo(20);
            assertThat(countRowsInTable(jdbcTemplate, "superhero_characters")).isEqualTo(32);
            assertThat(countRowsInTable(jdbcTemplate, "superheros_marvel")).isEqualTo(10);
            assertThat(countRowsInTable(jdbcTemplate, "superhero_ratings")).isEqualTo(1);
            assertThat(countRowsInTable(jdbcTemplate, "superhero_rating_tags")).isEqualTo(2);
        });
    }

    @Test
    void should_propagate_removals_from_mongo_to_pgsql() throws Exception {
        // given that data are already present
        loadData();
        initialImporter.start();

        assertThatInitialRowCountsAreAsExpected();

        // when watching for changes and removing data
        launchStreamerFromLastOplog();
        removeSuperheros("Superman", "Green Lantern", "Blue Beetle", "Hulk");

        // then
        awaitSomeTime().untilAsserted(() -> {
            assertThat(countRowsInTable(jdbcTemplate, "superheros")).isEqualTo(16);
            assertThat(countRowsInTable(jdbcTemplate, "superhero_characters")).isEqualTo(20);
            assertThat(countRowsInTable(jdbcTemplate, "superheros_marvel")).isEqualTo(9);
            assertThat(countRowsInTable(jdbcTemplate, "superhero_ratings")).isEqualTo(2);
            assertThat(countRowsInTable(jdbcTemplate, "superhero_rating_tags")).isEqualTo(3);
        });
    }

    private void assertThatInitialRowCountsAreAsExpected() {
        assertThat(countRowsInTable(jdbcTemplate, "superheros")).isEqualTo(20);
        assertThat(countRowsInTable(jdbcTemplate, "superhero_characters")).isEqualTo(33);
        assertThat(countRowsInTable(jdbcTemplate, "superheros_marvel")).isEqualTo(10);
        assertThat(countRowsInTable(jdbcTemplate, "superhero_ratings")).isEqualTo(4);
        assertThat(countRowsInTable(jdbcTemplate, "superhero_rating_tags")).isEqualTo(5);
    }

    private ConditionFactory awaitSomeTime() {
        return await().atMost(2, SECONDS);
    }

    private void removeSuperheros(String... superheros) {
        getCollection().deleteMany(new Document("superhero", new Document("$in", asList(superheros))));
    }

    private void clearData() {
        Stream.of("superhero_rating_tags", "superhero_ratings", "superheros_marvel", "superhero_characters", "superheros")
                .forEach(tableName -> jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName));

        getCollection().deleteMany(new Document());
    }

    private void launchStreamerFromLastOplog() {
        Optional<BsonTimestamp> checkpoint = Optional.of(checkpointManager.getLastOplog());
        streamerThread = new Thread(() -> oplogStreamer.watchFromCheckpoint(checkpoint));
        streamerThread.start();
    }

    private void loadData() throws IOException {
        List<Document> documents = loadDocuments(DATA_FILE_NAME);

        getCollection().insertMany(documents);
    }

    private void updateData() throws IOException {
        List<Document> documents = loadDocuments(DATA_UPDATE_FILE_NAME);
        MongoCollection<Document> collection = getCollection();

        documents.forEach(doc -> {
            String superhero = doc.getString("superhero");
            collection.findOneAndReplace(new Document("superhero", superhero), doc);
        });
    }

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase("my_db")
                .getCollection("superheros");
    }

    private List<Document> loadDocuments(String dataUpdateFileName) throws IOException {
        InputStream is = new ClassPathResource(dataUpdateFileName).getInputStream();
        JsonArray data = new JsonParser().parse(new InputStreamReader(is)).getAsJsonArray();
        List<Document> documents = new ArrayList<>(data.size());
        for (JsonElement jsonElement : data) {
            documents.add(Document.parse(jsonElement.toString()));
        }
        return documents;
    }
}