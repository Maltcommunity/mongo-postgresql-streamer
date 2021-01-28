package com.malt.mongopostgresqlstreamer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.malt.mongopostgresqlstreamer.resources.ResourceNotFoundException;
import com.malt.mongopostgresqlstreamer.resources.ResourceResolverService;

import java.io.*;
import java.net.URL;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceResolverServiceTest {

    private ResourceResolverService resourceResolverService;

    @BeforeEach
    void setUp() {
        resourceResolverService = new ResourceResolverService(new DefaultResourceLoader());
    }

    @Test
    void it_should_find_file_from_absolute_path() {
        String path = getMappingFilePath();
        verifyOutput(path);
    }

    @Test
    void it_should_find_file_from_path_containing_file_prefix() {
        String path = "file:" + getMappingFilePath();
        verifyOutput(path);
    }

    @Test
    void it_should_find_file_from_classpath() {
        String path = "classpath:mapping.json";
        verifyOutput(path);
    }

    @Test
    void it_should_find_file_from_classpath_without_prefix() {
        String path = "mapping.json";
        verifyOutput(path);
    }

    @Test
    void it_should_fail_if_file_does_not_exist() {
        assertThatThrownBy(() -> resourceResolverService.find("unknown_file.json"))
                .isExactlyInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resource 'unknown_file.json' cannot be found, please specify an absolute path or add file to the classpath.");
    }

    private String getMappingFilePath() {
        URL resource = getClass().getClassLoader().getResource("mapping.json");
        return Objects.requireNonNull(resource).getPath();
    }

    private void verifyOutput(String path) {
        InputStream is = resourceResolverService.find(path);
        assertThat(is).isNotNull();
        assertThat(read(is)).isNotNull().isNotEmpty().contains("my_mongo_database");
    }

    private static String read(InputStream is) {
        return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
    }
}
