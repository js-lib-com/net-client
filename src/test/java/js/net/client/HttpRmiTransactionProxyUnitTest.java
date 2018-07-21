package js.net.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Proxy;

import js.io.StreamHandler;
import js.net.client.fixture.MockConnectionFactory;
import js.net.client.fixture.MockConnectionFactory.OpenConnectionListener;
import js.net.client.fixture.MockHttpURLConnection;
import junit.framework.TestCase;

public class HttpRmiTransactionProxyUnitTest extends TestCase implements OpenConnectionListener {
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

	public void testGetPerson() {
		factory.responseStatusCode = 200;
		factory.responseContentType = "application/json";
		factory.responseBody = "{\"name\":\"John Doe\",\"age\":54}";

		Service service = getInstance(Service.class);
		Person person = service.getPerson();

		assertEquals("GET", connection.getRequestMethod());
		assertNull(connection.getRequestContentType());
		assertEquals("", connection.getRequestBody());

		assertNotNull(person);
		assertEquals("John Doe", person.name);
		assertEquals(54, person.age);
	}

	public void testSetPerson() throws InterruptedException {
		factory.responseStatusCode = 204;

		Service service = getInstance(Service.class);
		service.setPerson(new Person("Jane Doe", 33));
		Thread.sleep(500);

		assertEquals("POST", connection.getRequestMethod());
		assertEquals("application/json", connection.getRequestContentType());
		assertEquals("[{\"name\":\"Jane Doe\",\"age\":33}]", connection.getRequestBody());
	}

	@SuppressWarnings("unchecked")
	private <I> I getInstance(Class<? super I> interfaceClass) {
		return (I) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass }, new HttpRmiTransactionHandler(factory, "http://localhost/"));
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
