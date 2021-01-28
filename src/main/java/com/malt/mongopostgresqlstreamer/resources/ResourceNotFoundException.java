package com.malt.mongopostgresqlstreamer.resources;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String path) {
		super("Resource '" + path + "' cannot be found, please specify an absolute path or add file to the classpath.");
	}
}
