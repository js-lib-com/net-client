package com.jslib.net.client;

import com.jslib.rmi.RemoteFactory;
import com.jslib.rmi.RemoteFactoryProvider;

public class RemoteFactoryProviderImpl implements RemoteFactoryProvider {
	private static final String[] PROTOCOLS = new String[] { "http", "https" };
	private static final RemoteFactory FACTORY = new HttpRmiFactory();

	@Override
	public String[] getProtocols() {
		return PROTOCOLS;
	}

	@Override
	public RemoteFactory getRemoteFactory() {
		return FACTORY;
	}
}
