package js.net.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import js.json.Json;
import js.lang.AsyncTask;
import js.lang.BugError;
import js.lang.Callback;
import js.log.Log;
import js.log.LogFactory;
import js.net.client.encoder.ArgumentsWriter;
import js.net.client.encoder.ClientEncoders;
import js.net.client.encoder.ValueReader;
import js.rmi.BusinessException;
import js.rmi.RemoteException;
import js.rmi.RmiException;
import js.util.Classes;
import js.util.Files;
import js.util.Params;
import js.util.Strings;
import js.util.Types;

/**
 * Client transaction for remote method invocation using HTTP-RMI. This class adapts {@link HttpURLConnection} for remote method
 * invocation. Although public, this implementation is meant for this library internal usage. User is encouraged to invoke
 * remote methods via service provider interface and Java proxy, see {@link HttpRmiTransactionHandler}.
 * <p>
 * Anyway, if service provider does not supply an interface one may need to use this class, see sample code. It is developer
 * responsibility to ensure remote method signature is respected regarding class and method names, arguments order and types and
 * returned value type. Usually HTTP-RMI transaction is executed asynchronous, that is, {@link #exec(js.lang.Callback)} returns
 * immediately. Anyway, if callback is null transaction is executed synchronously.
 * 
 * <pre>
 * URL implementationURL = new URL(&quot;http://raspberrypi.local/&quot;);
 * HttpRmiTransaction rmi = HttpRmiTransaction.getInstance(implementationURL);
 * rmi.setMethod(&quot;net.dots.agent.Service&quot;, &quot;playSound&quot;);
 * rmi.setArguments(soundName);
 * rmi.exec(new Callback&lt;Boolean&gt;() {
 * 	&#064;Override
 * 	public void handle(Boolean returnValue) {
 * 
 * 	}
 * });
 * </pre>
 * 
 * <h3>Arguments Encoding</h3>
 * <p>
 * TODO document parameters encoding
 * </p>
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
public class HttpRmiTransaction {
	/** Class logger. */
	private static final Log log = LogFactory.getLog(HttpRmiTransaction.class);

	/**
	 * Create and open RMI transaction for requested URL.
	 * 
	 * @param implementationURL URL of the remote class implementation.
	 * @return RMI transaction instance.
	 */
	public static HttpRmiTransaction getInstance(String implementationURL) {
		return new HttpRmiTransaction(new ConnectionFactory(), implementationURL);
	}

	static {
		// there is a known bug on HttpURLConnection that retry on POST if not valid response or IOException
		// http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6382788
		//
		// on limit, it is potentially dangerous: if RST is injected by a router due to timeout it can result in data
		// duplication; to be on safe side this library disables HttpURLConnection POST retry

		// TOOD this property has no effect in Android
		System.setProperty("sun.net.http.retryPost", "false");
	}

	// ----------------------------------------------------
	// CONSTANTS

	/** Status code (200) indicating the request succeeded normally. */
	private static final int SC_OK = 200;

	/** Status code (204) for successful processing but no content returned. */
	private static final int SC_NO_CONTENT = 204;

	/** Status code (401) indicating that the request requires HTTP authentication. */
	private static final int SC_UNAUTHORIZED = 401;

	/** Status code (403) indicating the server understood the request but refused to fulfill it. */
	private static final int SC_FORBIDDEN = 403;

	/** Status code (404) indicating that the requested resource is not available. */
	private static final int SC_NOT_FOUND = 404;

	/** Status code (400) indicating the request sent by the client was syntactically incorrect. */
	private static final int SC_BAD_REQUEST = 400;

	/** Status code (500) indicating an error inside the HTTP server which prevented it from fulfilling the request. */
	private static final int SC_INTERNAL_SERVER_ERROR = 500;

	/** Status code (503) indicating that the HTTP server is temporarily overloaded, and unable to handle the request. */
	private static final int SC_SERVICE_UNAVAILABLE = 503;

	/** HTTP-RMI connection timeout, in milliseconds. */
	private static final int CONNECTION_TIMEOUT = 60000;

	/** HTTP-RMI read timeout, in milliseconds. */
	private static final int READ_TIMEOUT = 120000;

	/**
	 * Java session ID cookie pattern. THis pattern is observed on Tomcat and I do not know if standard for all Java application
	 * servers.
	 */
	private static final Pattern JSESSIONID_PATTERN = Pattern.compile(".*(JSESSIONID=[^;]+)(?:;.*)?$");

	/** Response header value for connection close. */
	private static final String CONNECTION_CLOSE = "close";

	/** Cookies on HTTP-RMI session. A session can span multiple transactions. */
	private static Map<URL, String> sessionCookies = new HashMap<URL, String>();

	// ----------------------------------------------------
	// INSTANCE FIELDS

	/** HTTP connection factory for both secure and non secure transactions. */
	private final ConnectionFactory connectionFactory;

	/** URL for host where remote method is deployed. */
	private final String implementationURL;

	/** Transaction URL connection used for transport. */
	private HttpURLConnection connection;

	/** Connection timeout in milliseconds. A value of 0 means block indefinitely. */
	private int connectionTimeout = CONNECTION_TIMEOUT;

	/** Read timeout in milliseconds. A value of 0 means block indefinitely. */
	private int readTimeout = READ_TIMEOUT;

	/**
	 * Remote method path ready to be inserted into request URI. It is compiled by {@link #setMethod(String, String)} based on
	 * given class and method names.
	 */
	private String methodPath;

	/** Actual arguments for remote method invocation. Types and order should be consistent with remote method signature. */
	private Object[] arguments;

	/** Remote method arguments writer. */
	private ArgumentsWriter argumentsWriter;

	/** Remote method exceptions list as declared into method signature. */
	private List<String> exceptions = new ArrayList<String>();

	/** Returned value type. This type should be consistent with remote method signature. */
	private Type returnType;

	/** Custom HTTP headers for special protocol extensions. */
	private Map<String, String> headers;

	/** User custom exception handler, default to null. */
	private volatile Callback<Throwable> exceptionHandler;

	/**
	 * Protected constructor.
	 * 
	 * @param connectionFactory HTTP connection factory,
	 * @param implementationURL remote class implementation URL, not null or empty.
	 * @throws IllegalArgumentException if <code>implementationURL</code> argument is null or empty.
	 */
	public HttpRmiTransaction(ConnectionFactory connectionFactory, String implementationURL) {
		Params.notNullOrEmpty(implementationURL, "Implementation URL");
		this.connectionFactory = connectionFactory;
		this.implementationURL = implementationURL;
	}

	/**
	 * Set connection timeout value, in milliseconds. Connection timeout value should be strict positive. This value should be
	 * set before transaction execution.
	 * 
	 * @param connectionTimeout connection timeout, milliseconds.
	 * @throws IllegalArgumentException if <code>connectionTimeout</code> argument is not strict positive.
	 * @since 1.9
	 */
	public void setConnectionTimeout(int connectionTimeout) throws IllegalArgumentException {
		Params.strictPositive(connectionTimeout, "Connection timeout");
		this.connectionTimeout = connectionTimeout;
	}

	/**
	 * Set read timeout value, in milliseconds. Read timeout value should be strict positive. This value should be set before
	 * transaction execution.
	 * 
	 * @param readTimeout read timeout in milliseconds, strict positive value.
	 * @throws IllegalArgumentException if <code>readTimeout</code> is not strict positive.
	 * @since 1.9
	 */
	public void setReadTimeout(int readTimeout) throws IllegalArgumentException {
		Params.strictPositive(readTimeout, "read timeout");
		this.readTimeout = readTimeout;
	}

	/**
	 * Set remote class and method. Compiles remote method path into format ready to be inserted into request URI, see
	 * {@link #methodPath}.
	 * <p>
	 * Method path format is described below. Note that extension is hard coded to <code>rmi</code>.
	 * 
	 * <pre>
	 * request-uri = class-name "/" method-name "." extension
	 * class-name = &lt; qualified class name using / instead of . &gt;
	 * method-name = &lt; method name &gt;
	 * extension = "rmi"
	 * </pre>
	 * 
	 * @param className remote qualified class name,
	 * @param methodName remote method name.
	 * @throws IllegalArgumentException if <code>className</code> argument is null or empty.
	 * @throws IllegalArgumentException if <code>methodName</code> argument is null or empty.
	 */
	public void setMethod(String className, String methodName) {
		Params.notNullOrEmpty(className, "Class name");
		Params.notNullOrEmpty(methodName, "Method name");

		StringBuilder builder = new StringBuilder();
		builder.append(Files.dot2urlpath(className));
		builder.append('/');
		builder.append(methodName);
		builder.append(".rmi");
		methodPath = builder.toString();
	}

	/**
	 * Set remote method invocation actual parameters. Parameters order and types should be consistent with remote method
	 * signature.
	 * 
	 * @param arguments variable number of actual arguments for remote method invocation.
	 * @throws IllegalArgumentException if given arguments is null or missing.
	 */
	public void setArguments(Object... arguments) {
		Params.notNullOrEmpty(arguments, "Arguments");
		this.arguments = arguments;
		argumentsWriter = ClientEncoders.getInstance().getArgumentsWriter(arguments);
	}

	/**
	 * Return true if transaction is forced to work in synchronous mode. As a general rule transaction is executed asynchronous
	 * if {@link #exec(Callback)} has not null callback. This means <code>exec</code> returns immediately and remote invocation
	 * results is supplied via callback instance. Anyway, depending on arguments encoder, is possible to force synchronous mode,
	 * e.g. dealing with stream requires synchronous mode to feed or consume the stream.
	 * 
	 * @return this default implementation always returns false.
	 */
	boolean isSynchronousForced() {
		return argumentsWriter != null && argumentsWriter.isSynchronous();
	}

	/**
	 * Set method exceptions list. This exceptions list is used by the logic that handle remote exception. It should be
	 * consistent with remote method signature.
	 * 
	 * @param exceptions method exceptions list.
	 */
	public void setExceptions(Class<?>[] exceptions) {
		for (Class<?> exception : exceptions) {
			// uses simple name because exception may be declared into .client package
			this.exceptions.add(exception.getSimpleName());
		}
	}

	/**
	 * Set custom exception handler for asynchronous transactions.
	 * 
	 * @param exceptionHandler custom exception handler, not null.
	 * @throws IllegalArgumentException if <code>exceptionHandler</code> is null.
	 */
	public void setExceptionHandler(Callback<Throwable> exceptionHandler) {
		Params.notNull(exceptionHandler, "Exception handler");
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Set expected type for the returned value. Returned type should be consistent with remote method signature.
	 * 
	 * @param returnType expected type for returned value.
	 * @throws IllegalArgumentException if <code>returnType</code> argument is null.
	 */
	public void setReturnType(Type returnType) {
		Params.notNull(returnType, "Return value type");
		this.returnType = returnType;
	}

	/**
	 * Set HTTP header value or remove named header if <code>value</code> is null.
	 * 
	 * @param name HTTP header name,
	 * @param value HTTP header value, possible null.
	 * @since 1.9
	 */
	public void setHeader(String name, String value) {
		if (headers == null) {
			headers = new HashMap<String, String>();
		}
		if (value != null) {
			headers.put(name, value);
		} else {
			headers.remove(name);
		}
	}

	/**
	 * Execute (a)synchronous remote method invocation.
	 * 
	 * @param callback optional callback for asynchronous mode, null for synchronous.
	 * @param <T> return value type.
	 * @return invocation return value for synchronous mode and always null for asynchronous invocations.
	 * @throws Exception all exceptions are bubbled up.
	 */
	@SuppressWarnings("unchecked")
	public <T> T exec(final Callback<T> callback) throws Exception {
		// Build request URL from remote class implementation URL and remote method name then delegate connection factory to
		// actually open the connection. Connection is stored into {@link #connection}.

		StringBuilder requestURL = new StringBuilder(implementationURL);
		if (!implementationURL.endsWith("/")) {
			requestURL.append("/");
		}
		requestURL.append(methodPath);
		connection = connectionFactory.openConnection(new URL(requestURL.toString()));

		// if no callback provided execute transaction synchronously
		if (callback == null) {
			return (T) exec();
		}

		AsyncTask<T> task = new AsyncTask<T>(callback) {
			@Override
			protected T execute() throws Throwable {
				return (T) HttpRmiTransaction.this.exec();
			}

			@Override
			protected void onThrowable(Throwable throwable) {
				super.onThrowable(throwable);
				if (HttpRmiTransaction.this.exceptionHandler != null) {
					HttpRmiTransaction.this.exceptionHandler.handle(throwable);
				}
			}
		};
		task.start();
		return null;
	}

	/**
	 * Executes transaction and returns remote value. Takes care to disconnect connection if server response has close header;
	 * also disconnect on any kind of error.
	 * 
	 * @return remote value.
	 * @throws Exception any exception on transaction processing is bubbled up to caller.
	 */
	private Object exec() throws Exception {
		boolean exception = false;
		try {
			return exec(connection);
		} catch (Throwable t) {
			log.dump(String.format("Error processing HTTP-RMI |%s|.", connection.getURL()), t);
			exception = true;
			throw t;
		} finally {
			if (exception || CONNECTION_CLOSE.equals(connection.getHeaderField("Connection"))) {
				connection.disconnect();
			}
		}
	}

	/**
	 * Execute synchronous remote method invocation using given HTTP connection.
	 * 
	 * @param connection HTTP connection to remote method.
	 * @return remote method return value.
	 * @throws Exception if transaction fails for any reasons be it client local, networking or remote process.
	 */
	private Object exec(HttpURLConnection connection) throws Exception {
		connection.setConnectTimeout(connectionTimeout);
		connection.setReadTimeout(readTimeout);

		connection.setRequestMethod(arguments == null ? "GET" : "POST");

		connection.setRequestProperty("User-Agent", "j(s)-lib/1.9.3");
		connection.setRequestProperty("Accept", "application/json, text/xml, application/octet-stream");
		connection.setRequestProperty("Pragma", "no-cache");
		connection.setRequestProperty("Cache", "no-cache");

		// do not set connection close request header in order to let HttpUrlConnection to transparently process persistent
		// connections, if server request so
		// note that on HTTP 1.1 all connections are considered persistent unless explicitly declared otherwise
		// anyway, if js.net.client.android system property is defined, force connection close
		if (System.getProperty("js.net.client.android") != null) {
			connection.setRequestProperty("Connection", "close");
		}

		if (headers != null) {
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				connection.setRequestProperty(entry.getKey(), entry.getValue());
			}
		}

		String sessionCookie = sessionCookies.get(connection.getURL());
		if (sessionCookie != null) {
			connection.setRequestProperty("Cookie", sessionCookie);
		}

		if (arguments != null) {
			connection.setDoOutput(true);
			String contentType = argumentsWriter.getContentType();
			if (contentType != null) {
				connection.setRequestProperty("Content-Type", contentType);
			}
			argumentsWriter.write(connection.getOutputStream(), arguments);
		}

		// at this point connection implementation perform the actual HTTP transaction: send request and wait for response,
		// parse response status code and headers and prepare a stream on response body
		int statusCode = connection.getResponseCode();

		// current RMI transaction implementation accept as success only 200 or 204 for void remote method
		if (statusCode != SC_OK && statusCode != SC_NO_CONTENT) {
			onError(statusCode);
			// error handler throws exception on any status code
		}

		String cookies = connection.getHeaderField("Set-Cookie");
		if (cookies != null) {
			Matcher matcher = JSESSIONID_PATTERN.matcher(cookies);
			if (matcher.find()) {
				sessionCookies.put(connection.getURL(), matcher.group(1));
			}
		}

		if (Types.isVoid(returnType)) {
			return null;
		}
		if (!Types.isVoid(returnType) && connection.getContentLength() == 0) {
			throw new BugError("Invalid HTTP-RMI transaction with |%s|. Expect return value of type |%s| but got empty response.", connection.getURL(), returnType);
		}

		ValueReader valueReader = ClientEncoders.getInstance().getValueReader(connection);
		try {
			return valueReader.read(connection.getInputStream(), returnType);
		}
		catch (SocketException e) {
			throw e;
		} catch (IOException e) {
			throw new BugError("Invalid HTTP-RMI transaction with |%s|. Response cannot be parsed to type |%s|. Cause: %s", connection.getURL(), returnType, e);
		}
	}

	/**
	 * Handle transaction error.
	 * 
	 * @param statusCode HTTP response status code.
	 * @throws IOException if reading connection error stream fails.
	 * @throws RmiException for all error codes less business exception and internal server error.
	 * @throws BusinessException if server side logic detects that a business constrain is broken.
	 * @throws Exception internal server error is due to a checked exception present into remote method signature.
	 */
	private void onError(int statusCode) throws Exception {
		// if status code is [200 300) range response body is accessible via getInputStream
		// otherwise getErrorStream should be used
		// trying to use getInputStream for status codes not in [200 300) range will rise IOException

		switch (statusCode) {
		case SC_FORBIDDEN:
			// server understood the request but refuses to fulfill it
			// compared with SC_UNAUTHORIZED, sending authentication will not grant access
			// common SC_FORBIDDEN condition may be Tomcat filtering by remote address and client IP is not allowed
			throw new RmiException("Server refuses to process request |%s|. Common cause may be Tomcat filtering by remote address and this IP is not allowed.", connection.getURL());

		case SC_UNAUTHORIZED:
			throw new RmiException("Attempt to access private remote method |%s| without authorization.", connection.getURL());

		case SC_NOT_FOUND:
			// not found may occur if front end Apache HTTP server does not recognize the protocol, e.g. trying to access
			// securely a
			// public method or because of misspelled extension; also virtual host configuration for remote context may be wrong
			throw new RmiException("Method |%s| not found. Check URL spelling, protocol unmatched or unrecognized extension.", connection.getURL());

		case SC_SERVICE_UNAVAILABLE:
			// front end HTTP server is running but application server is down; front end server responds with 503
			throw new RmiException("Front-end HTTP server is up but back-end is down. HTTP-RMI transaction |%s| aborted.", connection.getURL());

		case SC_BAD_REQUEST:
			if (isJSON(connection.getContentType())) {
				// business constrain not satisfied
				throw (BusinessException) readJsonObject(connection.getErrorStream(), BusinessException.class);
			}
			break;

		case SC_INTERNAL_SERVER_ERROR:
			if (isJSON(connection.getContentType())) {
				RemoteException remoteException = (RemoteException) readJsonObject(connection.getErrorStream(), RemoteException.class);
				log.error("HTTP-RMI error on |%s|: %s", connection.getURL(), remoteException);

				// if remote exception is an exception declared by method signature we throw it in this virtual machine
				if (exceptions.contains(getRemoteExceptionCause(remoteException))) {
					Class<? extends Throwable> cause = Classes.forOptionalName(remoteException.getCause());
					if (cause != null) {
						String message = remoteException.getMessage();
						if (message == null) {
							throw (Exception) Classes.newInstance(cause);
						}
						throw (Exception) Classes.newInstance(cause, remoteException.getMessage());
					}
				}

				// if received remote exception is not listed by method signature replace it with RmiException
				throw new RmiException(connection.getURL(), remoteException);
			}
		}

		final InputStream errorStream = connection.getErrorStream();
		if (errorStream != null) {
			String responseDump = Strings.load(errorStream, 100);
			log.error("HTTP-RMI error on |%s|. Server returned |%d|. Response dump:\r\n\t%s", connection.getURL(), statusCode, responseDump);
		} else {
			log.error("HTTP-RMI error on |%s|. Server returned |%d|.", connection.getURL(), statusCode);
		}
		throw new RmiException("HTTP-RMI error on |%s|. Server returned |%d|.", connection.getURL(), statusCode);
	}

	// ----------------------------------------------------
	// utility functions

	/**
	 * Test if content type describe JSON stream. Note that this library uses <code>application/json</code> as default value for
	 * content type. As a consequence null <code>contentType</code> parameter is accepted as JSON.
	 * 
	 * @param contentType content type, possible null.
	 * @return true if content type describe a JSON stream.
	 */
	private static boolean isJSON(String contentType) {
		return contentType == null || contentType.startsWith("application/json");
	}

	/**
	 * Read JSON object from input stream and return initialized object instance.
	 * 
	 * @param stream input stream,
	 * @param type expected type.
	 * @return object instance of requested type.
	 * @throws IOException if stream reading or JSON parsing fails.
	 */
	private static Object readJsonObject(InputStream stream, Type type) throws IOException {
		BufferedReader reader = null;
		Json json = Classes.loadService(Json.class);
		try {
			reader = Files.createBufferedReader(stream);
			return json.parse(reader, type);
		} finally {
			// do not use Files.close because we want to throw IOException is reader close fails
			if (reader != null) {
				reader.close();
			}
		}
	}

	/**
	 * Get the class simple name of the remote exception cause.
	 * 
	 * @param remoteException remote exception instance.
	 * @return remote exception cause simple name.
	 */
	private static String getRemoteExceptionCause(RemoteException remoteException) {
		return Strings.last(remoteException.getCause(), '.');
	}
}
