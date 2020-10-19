package js.net.client;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import js.json.JsonException;
import js.lang.BugError;
import js.lang.Event;
import js.net.client.fixture.Notification;
import junit.framework.TestCase;

public class EventReaderUnitTest extends TestCase {

	@Override
	protected void setUp() throws Exception {
		// TestWebAppContext.start();
	}

	public void testNotificationEvent() throws Exception {
		String eventStream = "" + //
				"event:js.net.client.fixture.Notification\r\n"+//
				"data:{\"id\":20,\"text\":\"Baby server was stopped.\",\"timestamp\":\"2014-05-31T10:19:28Z\"}\r\n" + //
				"\r\n";

		Event event = reader(eventStream).read();
		assertNotification(event);
	}

	public void testKeepAliveEvent() throws Exception {
		String eventStream = "" + //
				"data:\r\n" + //
				"\r\n" + //
				"event:js.net.client.fixture.Notification\r\n"+//
				"data:{\"id\":20,\"text\":\"Baby server was stopped.\",\"timestamp\":\"2014-05-31T10:19:28Z\"}\r\n" + //
				"\r\n";

		Event event = reader(eventStream).read();
		assertNotification(event);
	}

	private static void assertNotification(Event event) throws ParseException {
		assertNotNull(event);
		assertTrue(event instanceof Notification);
		Notification notification = (Notification) event;
		assertEquals(20, notification.id);
		assertEquals("Baby server was stopped.", notification.text);
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.setTimeInMillis(notification.timestamp.getTime());
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		assertEquals(df.parse("2014-05-31T10:19:28Z"), notification.timestamp);
	}

	public void _testClassNotFound() throws Exception {
		String eventStream = "" + //
				"data:{\"class\":\"comp.prj.Class\"}\r\n" + //
				"\r\n";

		boolean exception = false;
		try {
			reader(eventStream).read();
			fail("Not existing event class should throw ClassNotFoundException.");
		} catch (Exception e) {
			if (e instanceof ClassNotFoundException) {
				exception = true;
			}
		}
		assertTrue("Not existing event class should throw ClassNotFoundException.", exception);
	}

	public void testEmptyEventFieldValue() throws Exception {
		String eventStream = "" + //
				"event:\r\n" + //
				"data:{}\r\n" + //
				"\r\n";

		boolean exception = false;
		try {
			reader(eventStream).read();
			fail("Empty event field should throw IllegalStateException.");
		} catch (Exception e) {
			if (e instanceof IllegalStateException) {
				exception = true;
			}
		}
		assertTrue("Empty event field should throw IllegalStateException.", exception);
	}

	public void _testEmptyDataFieldValue() throws Exception {
		String eventStream = "" + //
				"event:js.net.client.fixture.Notification\r\n" + //
				"data:\r\n" + //
				"\r\n";

		boolean exception = false;
		try {
			reader(eventStream).read();
			fail("Empty data field should throw JsonParserException.");
		} catch (Exception e) {
			if (e instanceof JsonException) {
				exception = true;
			}
		}
		assertTrue("Empty data field should throw JsonParserException.", exception);
	}

	public void testInvalidDataFieldValue() throws Exception {
		String eventStream = "" + //
				"event:js.net.client.fixture.Notification\r\n" + //
				"data:{invalid}\r\n" + //
				"\r\n";

		boolean exception = false;
		try {
			reader(eventStream).read();
			fail("Invalid data field should throw JsonParserException.");
		} catch (Exception e) {
			if (e instanceof JsonException) {
				exception = true;
			}
		}
		assertTrue("Invalid data field should throw JsonParserException.", exception);
	}

	public void testBadFieldNameCase() throws Exception {
		String eventStream = "" + //
				"EVENT:js.net.client.fixture.Notification\r\n" + //
				"DATA:{}\r\n" + //
				"\r\n";

		boolean exception = false;
		try {
			reader(eventStream).read();
			fail("Bad field name case should throw IllegalStateException.");
		} catch (Exception e) {
			if (e instanceof IllegalStateException) {
				exception = true;
			}
		}
		assertTrue("Bad field name case should throw IllegalStateException.", exception);
	}

	public void _testBadFieldsOrder() throws Exception {
		String eventStream = "" + //
				"data:{}\r\n" + //
				"event:js.net.client.fixture.Notification\r\n" + //
				"\r\n";

		boolean exception = false;
		try {
			assertNull(reader(eventStream).read());
			fail("Bad fields order should throw IllegalStateException.");
		} catch (Exception e) {
			if (e instanceof IllegalStateException) {
				exception = true;
			}
		}
		assertTrue("Bad fields order should throw IllegalStateException.", exception);
	}

	/**
	 * Missing CRLF from the end of the event should return null, that is EOF.
	 */
	public void _testMissingEventCRLF() throws Exception {
		String eventStream = "" + //
				"data:{\"class\":\"js.net.client.fixture.Notification\"}\r\n";

		assertNull(reader(eventStream).read());
	}

	public void testMissingEventDataField() throws Exception {
		String eventStream = "" + //
				"event:js.net.client.fixture.Notification\r\n";

		assertNull(reader(eventStream).read());
	}

	public void testMissingEventDataFieldValue() throws Exception {
		String eventStream = "" + //
				"event:js.net.client.fixture.Notification\r\n" + //
				"data:";

		assertNull(reader(eventStream).read());
	}

	private static EventReader reader(String eventStream) {
		try {
			return new EventReader(new ByteArrayInputStream(eventStream.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			throw new BugError("JVM with missing support for UTF-8.");
		}
	}
}
