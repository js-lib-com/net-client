package com.jslib.net.client;

import java.lang.reflect.Proxy;

import com.jslib.rmi.RemoteFactory;
import com.jslib.util.Params;

/**
 * Factory for client proxy using {@link HttpRmiTransactionHandler} to transparently execute HTTP-RMI transactions.. Remote
 * class instance is a Java Proxy implementing the remote class interface and using {@link HttpRmiTransactionHandler} for actual
 * HTTP transaction execution.
 * 
 * @author Iulian Rotaru
 * @since 1.1
 * @version draft
 */
public final class HttpRmiFactory implements RemoteFactory {
	/**
	 * Create a new Java Proxy instance for a remote class implementing requested interface. In order to create a remote class
	 * instance one should know the URL of the context where the remote class is deployed. Also need to know service provider
	 * interface - see {@link com.jslib.net.client} package description, <em>Remote Service Client</em> section.
	 * 
	 * @param implementationURL the URL of remote implementation,
	 * @param interfaceClass remote class interface.
	 * @param <I> instance type.
	 * @return newly created Java Proxy instance.
	 * @throws IllegalArgumentException if <code>implementationURL</code> is null or empty.
	 * @throws IllegalArgumentException if <code>interfaceClass</code> is null.
	 */
	@SuppressWarnings("unchecked")
	public <I> I getRemoteInstance(Class<? super I> interfaceClass, String implementationURL) {
		Params.notNull(implementationURL, "Implementation URL");
		Params.notNull(interfaceClass, "Interface class");
		// at this point we know that interface class is a super of returned instance class so is safe to suppress warning
		return (I) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass }, new HttpRmiTransactionHandler(implementationURL));
	}
}
