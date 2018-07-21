package js.net.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import js.lang.BugError;
import js.lang.Callback;
import js.util.Types;

/**
 * Java Proxy invocation handler responsible for remote method invocation transaction execution. Proxy class bytecode is
 * generated at first invocation and cached and is used to create a Proxy instance for HTTP-RMI transaction - see
 * {@link HttpRmiFactory#getRemoteInstance(String, Class)}. Proxy instance does not store transaction state and can be reused.
 * <p>
 * In sample code, <code>service</code> instance is reused for both remote method invocations, executed at arbitrary points in
 * time.
 * 
 * <pre>
 * Service service = Factory.getInstance(&quot;http://smart-hub&quot;, Service.class);
 * . . .
 * service.invokeDeviceAction("heating-system", "getTemperature", new Callback&lt;Double&gt;() {
 * 	public void handle(Double value) {
 * 		. . .
 * });
 * . . .
 * service.invokeDeviceAction("heating-system", "setSetpoint", 22.5);
 * </pre>
 * <p>
 * Proxy instance delegates this transaction handler that takes care to create and execute remote method invocation transaction,
 * see {@link #invoke(Object, Method, Object[])} method. Remote execution is always performed in separated thread, i.e. is
 * asynchronous, even if remote method is void and user code does not supply a callback. Anyway, if remote method is not void
 * and {@link Callback} is missing transaction is executed synchronously. Caller should be aware that synchronous mode blocks
 * caller execution thread till remote method execution completes.
 * 
 * @author Iulian Rotaru
 * @since 1.1
 * @version draft
 */
public final class HttpRmiTransactionHandler implements InvocationHandler {
	/** Magic name for client packages used by service providers. */
	private static final String CLIENT_PACKAGE_SUFIX = ".client";

	private static final Callback<Object> VOID_CALLBACK = new Callback<Object>() {
		public void handle(Object value) {
		}
	};

	private final ConnectionFactory connectionFactory;

	/** URL of the remote class implementation. */
	private final String implementationURL;

	/**
	 * Construct transaction handler for a given remote class, identified by its implementation URL. Implementation URL is the
	 * context URL from the target host where remote class is deployed.
	 * 
	 * @param implementationURL URL of the remote class implementation.
	 */
	public HttpRmiTransactionHandler(String implementationURL) {
		this.connectionFactory = null;
		this.implementationURL = implementationURL;
	}

	public HttpRmiTransactionHandler(ConnectionFactory connectionFactory, String implementationURL) {
		this.connectionFactory = connectionFactory;
		this.implementationURL = implementationURL;
	}

	/**
	 * Invocation handler for remote method. Basically, creates a new remote invocation transaction, execute it asynchronously
	 * and return remote value into the callback given as invocation argument. If no callback supplied and the remote method is
	 * not void, remote invocation transaction is executed synchronously and this method blocks till execution completion.
	 * <p>
	 * Callback, if present, is supplied among invocation arguments list. It is an instance of {@link Callback} and it
	 * parameterized type should be consistent with remote method value type.
	 * 
	 * @param proxy Java Proxy instance,
	 * @param method reflective meta for remote method,
	 * @param arguments actual invocation arguments for remote method.
	 * @return remote invocation return value or null if callback is used.
	 * @throws Exception remote invocation exception, including local client, network or remote method execution.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Object invoke(Object proxy, Method method, Object[] arguments) throws Exception {
		// do not try to reuse remote invocation transaction; creates a new transaction instance for every invocation
		HttpRmiTransaction transaction = createTransaction(connectionFactory, implementationURL);

		String declaringClassName = method.getDeclaringClass().getCanonicalName();
		String packageName = method.getDeclaringClass().getPackage().getName();
		if (packageName.endsWith(CLIENT_PACKAGE_SUFIX)) {
			// if remote class is declared into client package just use parent package instead
			declaringClassName = declaringClassName.replace(CLIENT_PACKAGE_SUFIX, "");
		}
		transaction.setMethod(declaringClassName, method.getName());

		Type returnType = method.getGenericReturnType();
		Callback<Object> callback = null;

		// excerpt from Java API regarding this method third argument:
		// arguments - an array of objects containing the values of the arguments passed in the method invocation
		// on the Proxy instance or null if interface method takes no arguments.
		// to be on safe side test both null and empty conditions

		if (arguments != null && arguments.length > 0) {
			List<Object> remoteArguments = new ArrayList<Object>();

			for (int i = 0; i < arguments.length; ++i) {
				if (!(arguments[i] instanceof Callback)) {
					remoteArguments.add(arguments[i]);
					continue;
				}

				// here argument is the callback
				// it is expected to have a single callback; if more, uses the last one
				assert callback == null;
				callback = (Callback<Object>) arguments[i];

				// if callback is present uses its parameterized type as return type for the remote method
				// extract type parameter from actual argument instance, not from method signature that can contain wild card
				Type callbackType = arguments[i].getClass().getGenericInterfaces()[0];
				if (!(callbackType instanceof ParameterizedType)) {
					throw new BugError("Missing callback generic type. Cannot infer return type for |%s|.", method);
				}
				returnType = ((ParameterizedType) callbackType).getActualTypeArguments()[0];
			}

			if (!remoteArguments.isEmpty()) {
				transaction.setArguments(remoteArguments.toArray());
			}
		}

		transaction.setReturnType(returnType);
		transaction.setExceptions(method.getExceptionTypes());

		if (transaction.isSynchronousForced() || (!Types.isVoid(returnType) && callback == null)) {
			// force synchronous execution if specialized transaction request synchronous mode
			// also execute synchronous if remote method is not void but there is no callback
			// uses null callback to force synchronous
			return transaction.exec(null);
		}

		// usually user code does not provide callbacks when invoking void methods, although it may happen to want to know when
		// execution is complete in order, for example, to chain another
		// anyway, if no callback is provided uses the default void callback
		if (callback == null) {
			callback = VOID_CALLBACK;
		}

		transaction.exec(callback);
		return null;
	}

	private static HttpRmiTransaction createTransaction(ConnectionFactory connectionFactory, String implementationURL) {
		if (connectionFactory != null) {
			return new HttpRmiTransaction(connectionFactory, implementationURL);
		} else {
			return HttpRmiTransaction.getInstance(implementationURL);
		}
	}
}
