package js.net.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import js.lang.BugError;
import js.lang.Callback;
import js.lang.Event;
import js.lang.KeepAliveEvent;
import js.log.Log;
import js.log.LogFactory;
import js.util.Files;

/**
 * Open a connection with event stream Servlet then start reading events from input stream, in separated thread.
 * <p>
 * Using event stream client is straightforward: create event stream client instance then open connection using known event
 * stream URL and read events asynchronously. After job complete closes the client. Anyway, close is optional if end of stream
 * is controlled by server side. In this case event stream client stop itself and release resources.
 * 
 * <pre>
 * EventStreamClient client = new EventStreamClient();
 * client.open(new URL(&quot;http://hub.bbnet.ro/notification.event&quot;));
 * client.read(new Callback&lt;Event&gt;()
 * {
 *   public void handle(Event event)
 *   {
 *     Notification notification = (Notification)event;
 *     // handle notification
 *   }
 * });
 * . . .
 * client.close();
 * </pre>
 * 
 * This event stream client is not designed to work autonomously. It does not attempt to recover from networking exceptions not
 * even from read timeout. On any error, this class writes to error log and stops receiving thread. Finally, note that this
 * class uses {@link EventReader} to actually parse bytes from input stream. For details about event wire format please see
 * event reader class description.
 * 
 * @author Iulian Rotaru
 * @since 1.7
 * @version draft
 */
public class EventStreamClient implements Runnable {
	/** Class logger. */
	private static final Log log = LogFactory.getLog(EventStreamClient.class);

	/**
	 * Receiving thread start timeout. Receiving thread is started by {@link #read(Callback)} method that waits till thread is
	 * actually running. If this timeout expires read method returns anyway.
	 */
	private static final int THREAD_START_TIMEOUT = 2000;

	/**
	 * Receiving thread stop timeout. {@link #close()} method closes input stream and waits till receiving thread exits. It this
	 * timeout value is exceeded close method returns anyway.
	 */
	private static final int THREAD_STOP_TIMEOUT = 2000;

	/**
	 * Event stream server connection timeout. This timeout is used to guard {@link #open(URL)} method. If this timeout value is
	 * exceeded open method fails.
	 */
	private static final int CONNECTION_TIMEOUT = 10000;

	/**
	 * Input stream read timeout. This constant is directly correlated with event stream keep alive period. It must be updated
	 * when keep alive is changed and must be a little larger to compensate for systems loading and networking delays.
	 */
	private static final int READ_TIMEOUT = 50000;

	private final Object lock = new Object();

	private final Map<String, Class<? extends Event>> mappings = new HashMap<>();

	/** Receiving thread. */
	private Thread thread;

	/** Event stream server connection. */
	private HttpURLConnection connection;

	/** Input stream used to read events from. */
	private InputStream inputStream;

	/** Asynchronous callback invoked when new event is available or on receiving thread error. */
	private Callback<Event> callback;

	/** Default constructor. */
	public EventStreamClient() {
		mappings.put("KeepAliveEvent", KeepAliveEvent.class);
	}

	/**
	 * Construct event stream instance and opens it. This constructor is a convenient way to create event stream instance and
	 * open connection. In fact this constructor delegates {@link #open(URL)}.
	 * 
	 * @param eventStreamURL event stream URL.
	 * @throws IOException if event stream connection fails.
	 */
	public EventStreamClient(URL eventStreamURL) throws IOException {
		this();
		open(eventStreamURL);
	}

	public void addMapping(String eventName, Class<? extends Event> eventType) {
		mappings.put(eventName, eventType);
	}

	/**
	 * Opens connection with given event stream and initialize internal input stream. Because this method involve networking and
	 * server side processes it is not instant. Anyway, usually cannot exceed one second or two.
	 * 
	 * @param eventStreamURL event stream URL.
	 * @throws IOException if event stream connection fails including connection timeout.
	 */
	public void open(URL eventStreamURL) throws IOException {
		log.info("Connecting to event stream |%s|.", eventStreamURL);
		connection = (HttpURLConnection) eventStreamURL.openConnection();
		connection.setConnectTimeout(CONNECTION_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);

		connection.setRequestProperty("Accept", "text/event-stream");
		connection.setRequestProperty("Connection", "keep-alive");
		connection.setRequestProperty("Pragma", "no-cache");
		connection.setRequestProperty("Cache", "no-cache");

		// at this point HTTP URL connection implementation sends request and waits for response
		// then parses first line and headers and return input stream for body content
		inputStream = connection.getInputStream();
	}

	/**
	 * Read asynchronously events from connected event stream. This method start read thread and returns immediately. Given
	 * callback argument is used to pass events; when a new event arrives from server {@link Callback#handle(Object)} is
	 * invoked.
	 * <p>
	 * Please note that this method should be used on a connected event stream, i.e. after {@link #open(URL)} or auto-open
	 * constructor.
	 * 
	 * @param callback callback invoked on new event or on error.
	 * @throws BugError if attempt to use this method on a not connected event stream.
	 * @throws InterruptedException if waiting for thread start is interrupted.
	 */
	public void read(Callback<Event> callback) throws BugError, InterruptedException {
		if (inputStream == null) {
			throw new BugError("Attempt to read from a not connected event stream.");
		}
		this.callback = callback;
		thread = new Thread(this, getClass().getSimpleName());
		synchronized (lock) {
			thread.start();
			lock.wait(THREAD_START_TIMEOUT);
		}
	}

	/**
	 * Close this event stream client. This method stops receiving thread and closes internal input stream. It is not necessary
	 * if end of event stream is controlled by server side, in with case this event stream client does auto-close.
	 * 
	 * @throws InterruptedException if waiting for thread close is interrupted.
	 */
	public void close() throws InterruptedException {
		assert connection != null;
		if (connection != null) {
			Files.close(inputStream);
			if (thread.isAlive()) {
				synchronized (lock) {
					if (thread.isAlive()) {
						lock.wait(THREAD_STOP_TIMEOUT);
					}
				}
			}
		}
	}

	@Override
	public void run() {
		log.debug("Start event stream |%s| client on thread |%s|.", connection.getURL(), thread);
		synchronized (lock) {
			lock.notify();
		}

		long connectionStartTimestamp = System.currentTimeMillis();
		EventReader eventReader = new EventReader(mappings, inputStream);
		try {
			for (;;) {
				Event event = eventReader.read();
				if (event == null) {
					break;
				}
				callback.handle(event);
			}
		} catch (SocketTimeoutException e) {
			log.error("Event stream |%s read timeout.|", connection.getURL());
		} catch (SocketException e) {
			log.debug("Event reader closed due to socket exception: %s", e.getMessage());
		} catch (Throwable t) {
			log.error(t);
		} finally {
			Files.close(inputStream);
		}

		long connectionTime = System.currentTimeMillis() - connectionStartTimestamp;
		log.debug("Exit event stream |%s| reader loop. Connection time %d msec.", connection.getURL(), connectionTime);

		synchronized (lock) {
			lock.notify();
		}
		log.debug("Stop event stream |%s| client on thread |%s|.", connection.getURL(), thread);
	}
}
