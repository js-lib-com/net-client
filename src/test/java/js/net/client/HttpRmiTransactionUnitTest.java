package js.net.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;

import js.dom.Document;
import js.dom.DocumentBuilder;
import js.io.StreamHandler;
import js.net.client.fixture.MockConnectionFactory;
import js.net.client.fixture.MockConnectionFactory.OpenConnectionListener;
import js.net.client.fixture.MockHttpURLConnection;
import js.net.client.fixture.Notification;
import js.rmi.BusinessException;
import js.rmi.RemoteException;
import js.rmi.RmiException;
import js.util.Classes;
import junit.framework.TestCase;

public class HttpRmiTransactionUnitTest extends TestCase implements OpenConnectionListener {
	private MockConnectionFactory factory;
	private MockHttpURLConnection connection;
	private HttpRmiTransaction transaction;

	@Override
	public void onConnectionOpened(MockHttpURLConnection connection) {
		this.connection = connection;
	}

	public void testIsJSON() throws Exception {
		assertTrue((boolean) Classes.invoke(HttpRmiTransaction.class, "isJSON", "application/json"));
		// JSON media type does not require parameters and charset is ignored but implementation accept it
		assertTrue((boolean) Classes.invoke(HttpRmiTransaction.class, "isJSON", "application/json"));
		// null content type is accepted as default
		assertTrue((boolean) Classes.invoke(HttpRmiTransaction.class, "isJSON", (String) null));
		assertFalse((boolean) Classes.invoke(HttpRmiTransaction.class, "isJSON", ""));
		assertFalse((boolean) Classes.invoke(HttpRmiTransaction.class, "isJSON", "application/xml"));
	}

	public void testReadJsonObject() throws Exception {
		String json = "{\"id\":1964,\"text\":\"message\"}";
		Notification notification = Classes.invoke(HttpRmiTransaction.class, "readJsonObject", new ByteArrayInputStream(json.getBytes()), Notification.class);
		assertNotNull(notification);
		assertEquals(1964, notification.id);
		assertEquals("message", notification.text);
		assertNull(notification.timestamp);
	}

	public void testGetRemoteExceptionCause() throws Exception {
		RemoteException remoteException = new RemoteException(new IOException("Exception message."));
		String cause = Classes.invoke(HttpRmiTransaction.class, "getRemoteExceptionCause", remoteException);
		assertNotNull(cause);
		assertEquals("IOException", cause);
	}

	/** Set method should initialize internal method path. */
	public void testSetMethod() throws Exception {
		HttpRmiTransaction transaction = HttpRmiTransaction.getInstance("http://localhost/");
		transaction.setMethod("comp.prj.Class", "method");

		String methodPath = Classes.getFieldValue(transaction, "methodPath");
		assertNotNull(methodPath);
		assertEquals("comp/prj/Class/method.rmi", methodPath);
	}

	public void testSetMethodBadArguments() {
		assertMethodBadArguments(null, null);
		assertMethodBadArguments("", null);
		assertMethodBadArguments(null, "");
		assertMethodBadArguments("", "");
	}

	private static void assertMethodBadArguments(String className, String methodName) {
		HttpRmiTransaction transaction = HttpRmiTransaction.getInstance("http://localhost/");
		try {
			transaction.setMethod(className, methodName);
			fail("Bad method argument should rise illegal argument exception.");
		} catch (IllegalArgumentException e) {

		}
	}

	/** Set arguments should also initialize arguments encoder. */
	public void testSetArguments() {
		HttpRmiTransaction transaction = HttpRmiTransaction.getInstance("http://localhost/");
		assertNull(Classes.getFieldValue(transaction, "arguments"));
		assertNull(Classes.getFieldValue(transaction, "argumentsWriter"));

		transaction.setArguments(new Object());
		assertNotNull(Classes.getFieldValue(transaction, "arguments"));
		assertNotNull(Classes.getFieldValue(transaction, "argumentsWriter"));
	}

	public void testImplementationURL() throws Throwable {
		for (String implementationURL : new String[] { "http://localhost/app", "http://localhost/app/" }) {
			transaction = transaction(implementationURL);
			transaction.setMethod("comp.prj.Class", "method");
			exercise(200, "application/json", "true");
			assertEquals("http://localhost/app/comp/prj/Class/method.rmi", connection.getURL().toExternalForm());
		}
	}

	public void testConnectionTimeout() throws Throwable {
		String implementationURL = "http://192.168.1.253";

		transaction = HttpRmiTransaction.getInstance(implementationURL);
		transaction.setConnectionTimeout(1000);
		transaction.setMethod("js.hera.Switch", "turnON");
		transaction.setReturnType(Void.class);

		long timestamp = System.currentTimeMillis();
		try {
			transaction.exec(null);
			assertTrue("No socket timeout exception.", false);
		} catch (SocketTimeoutException e) {
			assertTrue("Connection timeout smaller than expected.", System.currentTimeMillis() - timestamp >= 1000);
			assertTrue("Connection timeout greater than expected.", System.currentTimeMillis() - timestamp < 1200);
		}
	}

	public void testNoParameters() throws Throwable {
		transaction = transaction();
		transaction.setMethod("js.test.Class", "method");
		transaction.setReturnType(Boolean.class);

		boolean value = exercise(200, "application/json", "true");

		assertTrue(value);
		assertEquals("http://localhost/test/js/test/Class/method.rmi", connection.getURL().toExternalForm());
		assertEquals("GET", connection.getRequestMethod());
		assertNull(connection.getRequestContentType());
		assertEquals("", connection.getRequestBody());
	}

	public void testJson() throws Throwable {
		transaction = transaction();
		transaction.setMethod("js.test.Class", "method");
		transaction.setArguments(1, 2);
		transaction.setReturnType(Boolean.class);

		boolean value = exercise(200, "application/json", "true");

		assertTrue(value);
		assertEquals("http://localhost/test/js/test/Class/method.rmi", connection.getURL().toExternalForm());
		assertEquals("POST", connection.getRequestMethod());
		assertEquals("application/json", connection.getRequestContentType());
		assertEquals("[1,2]", connection.getRequestBody());
	}

	public void testStream() throws Throwable {
		transaction = transaction();
		transaction.setMethod("js.test.Class", "method");
		transaction.setArguments(new StreamHandler<OutputStream>(OutputStream.class) {
			@Override
			protected void handle(OutputStream outputStream) throws IOException {
				outputStream.write("stream".getBytes("UTF-8"));
			}
		});
		transaction.setReturnType(Boolean.class);

		boolean value = exercise(200, "application/json", "true");

		assertTrue(value);
		assertEquals("http://localhost/test/js/test/Class/method.rmi", connection.getURL().toExternalForm());
		assertEquals("POST", connection.getRequestMethod());
		assertEquals("application/octet-stream", connection.getRequestContentType());
		assertEquals("stream", connection.getRequestBody());
	}

	public void testDocument() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");
		DocumentBuilder builder = Classes.loadService(DocumentBuilder.class);
		Document document = builder.createXML("root");
		transaction.setArguments(document);
		transaction.setReturnType(Boolean.class);

		boolean value = exercise(200, "application/json", "true");

		assertTrue(value);
		assertEquals("http://localhost/test/js/test/Class/method.rmi", connection.getURL().toExternalForm());
		assertEquals("POST", connection.getRequestMethod());
		assertEquals("text/xml; charset=UTF-8", connection.getRequestContentType());
		assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<root></root>", connection.getRequestBody());
	}

	public void testMixed() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");
		DocumentBuilder builder = Classes.loadService(DocumentBuilder.class);
		transaction.setArguments(1, builder.createXML("root"), new StreamHandler<OutputStream>(OutputStream.class) {
			@Override
			protected void handle(OutputStream outputStream) throws IOException {
				outputStream.write("stream".getBytes("UTF-8"));
			}
		});
		transaction.setReturnType(Boolean.class);

		boolean value = exercise(200, "application/json", "true");

		Object argumentsEncoder = Classes.getFieldValue(transaction, "argumentsWriter");
		String boundary = Classes.getFieldValue(argumentsEncoder, "boundary");

		assertTrue(value);
		assertEquals("http://localhost/test/js/test/Class/method.rmi", connection.getURL().toExternalForm());
		assertEquals("POST", connection.getRequestMethod());
		assertEquals(String.format("multipart/mixed; boundary=\"%s\"", boundary), connection.getRequestContentType());

		String expectedRequestBody = "" + //
				"\r\n" + //
				"--%1$s\r\n" + //
				"Content-Disposition: form-data; name=\"0\"\r\n" + //
				"Content-Type: application/json\r\n" + //
				"\r\n" + //
				"1\r\n" + //
				"--%1$s\r\n" + //
				"Content-Disposition: form-data; name=\"1\"\r\n" + //
				"Content-Type: text/xml; charset=UTF-8\r\n" + //
				"\r\n" + //
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + //
				"<root></root>\r\n" + //
				"--%1$s\r\n" + //
				"Content-Disposition: form-data; name=\"2\"\r\n" + //
				"Content-Type: application/octet-stream\r\n" + //
				"\r\n" + //
				"stream\r\n" + //
				"--%1$s--";
		assertEquals(String.format(expectedRequestBody, boundary), connection.getRequestBody());
	}

	public void testForbiden() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");

		try {
			exercise(403, null, null);
		} catch (RmiException e) {
			if (e.getMessage().startsWith("Server refuses to process request")) {
				return;
			}
		}
		fail("Server response 403 should rise RMI exception.");
	}

	public void testCheckedRemoteException() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");
		transaction.setReturnType(Boolean.class);
		transaction.setExceptions(new Class<?>[] { IOException.class });

		try {
			exercise(500, "application/json", "{\"cause\":\"java.io.IOException\",\"message\":\"server exception\"}");
		} catch (IOException e) {
			assertEquals("server exception", e.getMessage());
			return;
		}
		fail("Checked remote exception should rise checked exception.");
	}

	public void testUnauthorized() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");

		try {
			exercise(401, null, null);
		} catch (RmiException e) {
			if (e.getMessage().startsWith("Attempt to access private")) {
				return;
			}
		}
		fail("Server response 401 should rise RMI exception.");
	}

	public void testNotFound() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");

		try {
			exercise(404, null, null);
		} catch (RmiException e) {
			if (e.getMessage().startsWith("Method |http://localhost/test/js/test/Class/method.rmi| not found")) {
				return;
			}
		}
		fail("Server response 404 should rise RMI exception.");
	}

	public void testBadRequest() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");
		transaction.setReturnType(Boolean.class);
		transaction.setExceptions(new Class<?>[] { IOException.class });

		try {
			exercise(400, "application/json", "{\"errorCode\":1964}");
		} catch (BusinessException e) {
			assertEquals("0x000007AC", e.getMessage());
			return;
		}
		fail("Checked remote exception should rise checked exception.");
	}

	/**
	 * Service not available - 503 is observed to be sent back when front-end HTTP server is up but back-end web server is down.
	 */
	public void testServiceUnavailable() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");

		try {
			exercise(503, null, null);
		} catch (RmiException e) {
			if (e.getMessage().startsWith("Front-end HTTP server is up but back-end is down.")) {
				return;
			}
		}
		fail("Server response 503 should rise RMI exception.");
	}

	/** Server responds with 500 and an exception declared into method signature. */
	public void testInternalServerErrorCheckedException() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");
		transaction.setExceptions(new Class<?>[] { IOException.class });

		try {
			exercise(500, "application/json", "{\"cause\":\"java.io.IOException\",\"message\":\"Error message.\"}");
		} catch (IOException e) {
			if (e.getMessage().equals("Error message.")) {
				return;
			}
		}
		fail("Server response 500 should rethrow original remote exception.");
	}

	/** Server responds with 500 and an exception not present into method signature. */
	public void testInternalServerErrorUncheckedException() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");
		transaction.setReturnType(Boolean.class);

		try {
			exercise(500, "application/json", "{\"cause\":\"java.io.IOException\",\"message\":\"Error message.\"}");
		} catch (Exception e) {
			assertTrue(e instanceof RmiException);
			assertEquals("HTTP-RMI server execution error on |http://localhost/test/js/test/Class/method.rmi|: java.io.IOException: Error message.", e.getMessage());
			return;
		}
		fail("Unchecked remote exception should rise RMI exception.");
	}

	public void testUnexpectedErrorCode() throws Throwable {
		transaction = transaction("http://localhost/test/");
		transaction.setMethod("js.test.Class", "method");

		try {
			exercise(567, "text/plain; charset=UTF-8", "Unknown code.");
		} catch (RmiException e) {
			if (e.getMessage().startsWith("HTTP-RMI error on |http://localhost/test/js/test/Class/method.rmi|. Server returned |567|.")) {
				return;
			}
		}
		fail("Server response 567 should rise RMI exception.");
	}

	// -------------------------------------------------------

	/**
	 * Create HTTP-RMI transaction with connection factory replaced by mock.
	 * 
	 * @param implementationURL
	 * @return HTTP-RMI transaction instance.
	 */
	private HttpRmiTransaction transaction(String... implementationURL) throws MalformedURLException {
		factory = new MockConnectionFactory(this);
		return new HttpRmiTransaction(factory, implementationURL.length > 0 ? implementationURL[0] : "http://localhost/test/");
	}

	/**
	 * Execute HTTP-RMI transaction with mock HttpURLConnection configured with response status code, content type and body.
	 * 
	 * @param statusCode response status code,
	 * @param contentType response content type,
	 * @param body response body.
	 */
	@SuppressWarnings("unchecked")
	private <T> T exercise(int statusCode, String contentType, String body) throws Throwable {
		factory.responseStatusCode = statusCode;
		factory.responseContentType = contentType;
		factory.responseBody = body;

		return (T) transaction.exec(null);
	}
}
