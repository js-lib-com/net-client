package js.net.client.fixture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import js.lang.BugError;

/**
 * Mock HttpURLConnection does not perform net activities but returns configurable status code, content type and body.
 * 
 * @author Iulian Rotaru
 */
public class MockHttpURLConnection extends HttpURLConnection {
	private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	private int responseStatusCode;
	private String responseContentType;
	private String responseBody;

	public MockHttpURLConnection(URL url) {
		super(url);
	}

	public String getRequestContentType() {
		return getRequestProperty("Content-Type");
	}

	public String getRequestBody() {
		return outputStream.toString();
	}

	public int getResponseStatusCode() {
		return responseStatusCode;
	}

	public void setResponseStatusCode(int responseStatusCode) {
		this.responseStatusCode = responseStatusCode;
	}

	public String getResponseContentType() {
		return responseContentType;
	}

	public void setResponseContentType(String responseContentType) {
		this.responseContentType = responseContentType;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}

	public String getResponseBody() {
		return responseBody;
	}

	@Override
	public String getContentType() {
		return responseContentType;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return outputStream;
	}

	@Override
	public int getResponseCode() throws IOException {
		return responseStatusCode;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		if (responseBody == null) {
			throw new BugError("Request input stream from a mock connection without response body.");
		}
		return new ByteArrayInputStream(responseBody.getBytes("UTF-8"));
	}

	@Override
	public InputStream getErrorStream() {
		try {
			return getInputStream();
		} catch (IOException e) {
		}
		return null;
	}

	@Override
	public void disconnect() {
	}

	@Override
	public boolean usingProxy() {
		return false;
	}

	@Override
	public void connect() throws IOException {
	}
}
