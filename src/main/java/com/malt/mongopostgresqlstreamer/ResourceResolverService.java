package com.malt.mongopostgresqlstreamer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class ResourceResolverService {

	private final List<ResourceLoader> resourceResolvers;

	@Inject
	public ResourceResolverService(ResourceLoader fallbackResourceResolver) {
		this.resourceResolvers = new ArrayList<>();
		this.resourceResolvers.add(new AbsoluteFileSystemResourceLoader());
		this.resourceResolvers.add(new FileSystemResourceLoader());
		this.resourceResolvers.add(new PathMatchingResourcePatternResolver());
		this.resourceResolvers.add(fallbackResourceResolver);
	}

	InputStream find(String path) {
		for (ResourceLoader resourceLoader : resourceResolvers) {
			InputStream is = find(path, resourceLoader);
			if (is != null) {
				return is;
			}
		}

		throw new ResourceNotFoundException(path);
	}

	private static InputStream find(String path, ResourceLoader resourceLoader) {
		try {
			Resource resource = resourceLoader.getResource(path);
			return resource.exists() ? resource.getInputStream() : null;
		} catch (IOException ex) {
			return null;
		}
	}

	private static class AbsoluteFileSystemResourceLoader extends FileSystemResourceLoader {
		@Override
		protected Resource getResourceByPath(String path) {
			return new FileSystemResource(path);
		}
	}
}
