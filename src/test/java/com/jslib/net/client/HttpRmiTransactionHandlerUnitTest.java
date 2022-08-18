package com.jslib.net.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jslib.io.StreamHandler;
import com.jslib.net.client.fixture.MockConnectionFactory;
import com.jslib.net.client.fixture.MockConnectionFactory.OpenConnectionListener;
import com.jslib.net.client.fixture.MockHttpURLConnection;
import com.jslib.util.Classes;
import com.jslib.util.Files;
import com.jslib.util.Strings;

import junit.framework.TestCase;

public class HttpRmiTransactionHandlerUnitTest extends TestCase implements OpenConnectionListener {
	private MockConnectionFactory factory;
	private MockHttpURLConnection connection;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		factory = new MockConnectionFactory(this);
	}

	@Override
	public void onConnectionOpened(MockHttpURLConnection connection) {
		this.connection = connection;
	}

	public void testSetObject() throws Throwable {
		factory.responseStatusCode = 204;
		invoke("setPerson", new Person("Jane Doe", 33));
		Thread.sleep(500);

		assertEquals("POST", connection.getRequestMethod());
		assertEquals("application/json", connection.getRequestContentType());
		assertEquals("[{\"name\":\"Jane Doe\",\"age\":33}]", connection.getRequestBody());
	}

	public void testGetObject() throws Throwable {
		factory.responseStatusCode = 200;
		factory.responseContentType = "application/json";
		factory.responseBody = "{\"name\":\"John Doe\",\"age\":54}";

		Person person = invoke("getPerson");
		assertEquals("GET", connection.getRequestMethod());
		assertNull(connection.getRequestContentType());
		assertEquals("", connection.getRequestBody());

		assertNotNull(person);
		assertEquals("John Doe", person.name);
		assertEquals(54, person.age);
	}

	public void testDownload() throws Throwable {
		factory.responseStatusCode = 200;
		factory.responseContentType = "application/octet-stream";
		factory.responseBody = "download text";

		InputStream inputStream = invoke("download");
		assertEquals("GET", connection.getRequestMethod());
		assertNull(connection.getRequestContentType());
		assertEquals("", connection.getRequestBody());

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		Files.copy(inputStream, outputStream);
		assertEquals("download text", outputStream.toString());
	}

	public void testUpload() throws Throwable {
		factory.responseStatusCode = 204;

		invoke("upload", new StreamHandler<OutputStream>(OutputStream.class) {
			@Override
			protected void handle(OutputStream outputStream) throws IOException {
				Strings.save("upload text", outputStream);
			}
		});
		Thread.sleep(500);

		assertEquals("POST", connection.getRequestMethod());
		assertEquals("application/octet-stream", connection.getRequestContentType());
		assertEquals("upload text", connection.getRequestBody());
	}

	public void testForceSynchronous() throws Throwable {
		factory.responseStatusCode = 204;

		final Thread invokerThread = Thread.currentThread();
		final AtomicBoolean probe = new AtomicBoolean(false);
		invoke("upload", new StreamHandler<OutputStream>(OutputStream.class) {
			@Override
			protected void handle(OutputStream outputStream) throws IOException {
				if (invokerThread.equals(Thread.currentThread())) {
					probe.set(true);
				}
			}
		});

		assertTrue("Stream arguments should force HTTP-RMI transaction in synchronous mode.", probe.get());
	}

	@SuppressWarnings("unchecked")
	private <T> T invoke(String methodName, Object... arguments) throws Throwable {
		HttpRmiTransactionHandler handler = new HttpRmiTransactionHandler(factory, "http://localhost/");
		Method method = Service.class.getMethod(methodName, Classes.getParameterTypes(arguments));
		Object value = handler.invoke(null, method, arguments);
		return (T) value;
	}

	private static class Person {
		String name;
		int age;

		@SuppressWarnings("unused")
		public Person() {
		}

		public Person(String name, int age) {
			this.name = name;
			this.age = age;
		}
	}

	private static interface Service {
		Person getPerson();

		void setPerson(Person person);

		InputStream download();

		void upload(StreamHandler<OutputStream> files);
	}
}
