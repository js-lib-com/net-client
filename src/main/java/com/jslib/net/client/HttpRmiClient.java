package com.jslib.net.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.jslib.lang.BugError;

public class HttpRmiClient {
	private final HttpRmiTransaction transaction;
	private final String className;

	public HttpRmiClient(String implementationURL, String className) {
		this.transaction = new HttpRmiTransaction(new DefaultConnectionFactory(), implementationURL);
		this.transaction.setReturnType(Void.class);
		this.className = className;
	}

	public void setHttpHeader(String name, String value) {
		transaction.setHeader(name, value);
	}

	public void setConnectionTimeout(int connectionTimeout) {
		transaction.setConnectionTimeout(connectionTimeout);
	}

	public void setResponseTimeout(int responseTimeout) {
		transaction.setReadTimeout(responseTimeout);
	}

	public void setReturnType(Type returnType) {
		transaction.setReturnType(returnType);
	}

	public void setExceptions(Class<?>... exceptions) {
		transaction.setExceptions(exceptions);
	}

	public <T> T invoke(String methodName, Object... arguments) throws Exception {
		transaction.setMethod(className, methodName);
		if (arguments.length != 0) {
			transaction.setArguments(arguments);
		}
		return transaction.exec(null);
	}

	private static class DefaultConnectionFactory extends ConnectionFactory {
		@Override
		public HttpURLConnection openConnection(URL url) throws IOException {
			if (!isSecure(url.getProtocol())) {
				return (HttpURLConnection) url.openConnection();
			}

			TrustManager tm = new X509TrustManager() {
				@Override
				public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1) throws java.security.cert.CertificateException {
				}

				@Override
				public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1) throws java.security.cert.CertificateException {
				}

				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};

			SSLContext sslContext;
			try {
				sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, new TrustManager[] { tm }, null);
				HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
				connection.setSSLSocketFactory(sslContext.getSocketFactory());
				return connection;
			} catch (Throwable e) {
				new IOException(e);
			}

			// make compiler happy; we never step here
			return null;
		}
	}

	private static final String HTTP = "http";

	private static final String HTTPS = "https";

	private static boolean isSecure(String protocol) throws BugError {
		if (HTTP.equalsIgnoreCase(protocol)) {
			return false;
		} else if (HTTPS.equalsIgnoreCase(protocol)) {
			return true;
		} else {
			throw new BugError("Unsupported protocol |%s| for HTTP transaction.", protocol);
		}
	}
}
