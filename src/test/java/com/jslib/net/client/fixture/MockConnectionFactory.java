package com.jslib.net.client.fixture;

import java.net.HttpURLConnection;
import java.net.URL;

import com.jslib.net.client.ConnectionFactory;

/**
 * Mock connection factory replaces HttpURLConnection with {@link MockHttpURLConnection}.
 * 
 * @author Iulian Rotaru
 */
public class MockConnectionFactory extends ConnectionFactory {
	public int responseStatusCode;
	public String responseContentType;
	public String responseBody;

	private OpenConnectionListener listener;

	public MockConnectionFactory(OpenConnectionListener listener) {
		super();
		this.listener = listener;
	}

	@Override
	public HttpURLConnection openConnection(URL url) {
		MockHttpURLConnection connection = new MockHttpURLConnection(url);
		connection.setResponseStatusCode(responseStatusCode);
		connection.setResponseContentType(responseContentType);
		connection.setResponseBody(responseBody);
		if (listener != null) {
			listener.onConnectionOpened(connection);
		}
		return connection;
	}

	public static interface OpenConnectionListener {
		void onConnectionOpened(MockHttpURLConnection connection);
	}
}
