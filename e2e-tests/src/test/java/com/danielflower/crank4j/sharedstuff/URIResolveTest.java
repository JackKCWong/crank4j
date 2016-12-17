package com.danielflower.crank4j.sharedstuff;

import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.Matchers.is;

public class URIResolveTest {

	@Test
	public  void urlResolveTest() throws MalformedURLException, URISyntaxException {
		String url = "/some//path";
		Assert.assertThat(URI.create(url).normalize().toString(), is("/some/path"));
	}
}
