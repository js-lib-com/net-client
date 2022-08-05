package com.jslib.net.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP connection factory. This class is a simple wrapper for {@link URL#openConnection()}. It exists only to facilitate mock
 * creation on unit tests.
 * 
 * @author Iulian Rotaru
 * @since 1.7
 */
public class ConnectionFactory {
	public HttpURLConnection openConnection(URL url) throws IOException {
		return (HttpURLConnection) url.openConnection();
	}
}
