package js.net.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import js.json.Json;
import js.json.JsonException;
import js.lang.Event;
import js.lang.KeepAliveEvent;
import js.log.Log;
import js.log.LogFactory;
import js.util.Base64;
import js.util.Classes;
import js.util.Files;

/**
 * Client reader for W3C server sent events. This reader implements event stream parser as described by W3C Server-Sent Events
 * section 6. It is designed for {@link EventStreamClient} to actually extract events from the event stream; anyway this class
 * is public for the benefit of potential custom event stream clients.
 * <p>
 * This class is designed as a client counterpart for event stream from server side logic. Because this library imposes couple
 * constrains to W3C events stream grammar, see package description, this parser cannot be used to read from 3pty server
 * implementations.
 * <p>
 * For quick event format overview see below sample, as it is on the wire:
 * 
 * <pre>
 * data:{"class":"bbnet.hub.Alert","id":123,"title":"BaBy NET Alert","text":"Server is down."}CRLF
 * CRLF
 * </pre>
 * <p>
 * Finally, this parser takes care of <em>keep alive</em> events, if any: ignores them and continue waiting for next event.
 * 
 * @author Iulian Rotaru
 * @see <a href="http://www.w3.org/TR/2009/WD-eventsource-20091222/">W3C Server-Sent Events</a>
 * @version draft
 */
public class EventReader {
	/** Class logger. */
	private static final Log log = LogFactory.getLog(EventReader.class);

	/** Internal socket reader. */
	private final BufferedReader reader;

	/** JSON (de)serializer. */
	private final Json json;

	/** Optional symmetric key used to encrypt the event message using AES chiper. Default to null. */
	private final SecretKey secretKey;

	/**
	 * Construct plain text event reader instance wrapping given input stream.
	 * 
	 * @param inputStream input stream carrying events.
	 */
	public EventReader(InputStream inputStream) {
		this(inputStream, null);
	}

	/**
	 * Construct secure event reader.
	 * 
	 * @param inputStream input stream carrying events,
	 * @param secretKey secret key for symmetric encryption.
	 */
	public EventReader(InputStream inputStream, SecretKey secretKey) {
		// important note:

		// not fully understood but is possible to loss data if do not use buffered reader
		// is like input stream reader reads from socket and if there is no one to handle those bytes, they are lost
		// above explanation is merely to describe the context but below facts are real on android

		// when there are couple events arrived in burst all events after first one are simple lost
		// if sent events list with some delay between, everything works as expected
		// after added buffered reader everything is all right, even with large list of events

		this.json = Classes.loadService(Json.class);
		this.reader = Files.createBufferedReader(inputStream);
		this.secretKey = secretKey;
	}

	/**
	 * Wait for next event from events stream, parse and return it. If received event is happening to be <em>keep alive</em>,
	 * ignore it and continue waiting for next event.
	 * 
	 * @return server sent event or null for end of file.
	 * @throws IOException if read from events stream fails for any reason, including read timeout, if stream is configured
	 *             with.
	 * @throws ClassNotFoundException if parsed event class is not found in this virtual machine.
	 * @throws IllegalStateException if given events stream does not obey the grammar, as described into this class description.
	 * @throws NoSuchPaddingException if no encryption padding implementation not found.
	 * @throws NoSuchAlgorithmException if there is no support for <code>AES</code> encryption.
	 * @throws InvalidKeyException if {@link #secretKey} is invalid.
	 * @throws BadPaddingException if encryption padding fails.
	 * @throws IllegalBlockSizeException invalid encryption block length.
	 */
	public Event read() throws IOException, ClassNotFoundException, JsonException, IllegalStateException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		StringBuilder eventBuilder = new StringBuilder();
		Event event = null;
		State state = State.NEW_EVENT;
		Field field = Field.NONE;

		EVENT_READ_LOOP: for (;;) {
			int i = reader.read();
			if (i == -1) {
				return null;
			}
			char c = (char) i;

			switch (state) {
			case NEW_EVENT:
				assert field == Field.NONE;
				state = State.NEW_FIELD;

			case NEW_FIELD:
				if (c == '\r') {
					// empty field mark event end
					state = State.EVENT_LF;
					break;
				}
				switch (c) {
				case 'e':
					field = Field.EVENT;
					break;
				case 'd':
					field = Field.DATA;
					break;
				case 'i':
					field = Field.ID;
					break;
				case 'r':
					field = Field.RETRY;
					break;
				default:
					throw new IllegalStateException();
				}
				state = State.WAIT_COLON;
				break;

			case WAIT_COLON:
				if (c != ':') {
					continue;
				}
				switch (field) {
				case EVENT:
					state = State.EVENT;
					break;
				case DATA:
					state = State.DATA;
					this.reader.mark(1);
					break;
				case ID:
					state = State.ID;
					break;
				case RETRY:
					state = State.RETRY;
					break;
				case NONE:
					throw new IllegalStateException();
				}
				break;

			case EVENT:
				if (c != '\r') {
					eventBuilder.append(c);
				} else {
					if (eventBuilder.toString().isEmpty()) {
						log.debug("Event field is empty");
						throw new IllegalStateException();
					}
					state = State.FIELD_LF;
				}
				break;

			case DATA:
				reader.reset();

				// data:jsonCRLFCRLF, data:base64CRLFCRLF or data:CRLFCRLF for keep alive
				String message = this.reader.readLine();
				this.reader.readLine(); // extract event end from stream

				if (message.isEmpty()) {
					// keep alive
					state = State.NEW_EVENT;
					field = Field.NONE;
					continue;
				}

				if (this.secretKey != null) {
					Cipher cipher = Cipher.getInstance("AES");
					cipher.init(Cipher.DECRYPT_MODE, secretKey);
					message = new String(cipher.doFinal(Base64.decode(message)));
				}

				// TODO: server stream event name is simple, not qualified; it cannot be used for class load
				event = json.parse(message, Classes.forName(eventBuilder.toString()));
				break EVENT_READ_LOOP;

			case ID:
			case RETRY:
				if (c == '\r') {
					state = State.FIELD_LF;
				}
				break;

			case FIELD_CR:
				if (c != '\r') {
					throw new IllegalStateException();
				}
				state = State.FIELD_LF;
				break;

			case FIELD_LF:
				if (c != '\n') {
					throw new IllegalStateException();
				}
				state = State.NEW_FIELD;
				break;

			case EVENT_CR:
				if (c != '\r') {
					throw new IllegalStateException();
				}
				state = State.EVENT_LF;
				break;

			case EVENT_LF:
				if (c != '\n') {
					throw new IllegalStateException();
				}
				if (event instanceof KeepAliveEvent) {
					eventBuilder.setLength(0);
					state = State.NEW_EVENT;
					field = Field.NONE;
				} else {
					break EVENT_READ_LOOP;
				}
			}
		}

		return event;
	}

	/**
	 * Events parser state machine.
	 * 
	 * @author Iulian Rotaru
	 * @version draft
	 */
	private static enum State {
		NEW_EVENT, NEW_FIELD, WAIT_COLON, EVENT, DATA, ID, RETRY, FIELD_CR, FIELD_LF, EVENT_CR, EVENT_LF
	}

	/**
	 * Events stream fields.
	 * 
	 * @author Iulian Rotaru
	 * @version draft
	 */
	private static enum Field {
		NONE, EVENT, DATA, ID, RETRY
	}
}
