package com.malt.mongopostgresqlstreamer.consumers.dump.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Header {
	@JsonProperty("concurrent_collections")
	public Long concurrentCollections;
	public String version;
	@JsonProperty("server_version")
	public String serverVersion;
	@JsonProperty("tool_version")
	public String toolVersion;
}
