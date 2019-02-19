package js.net.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.ServiceLoader;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import js.lang.BugError;
import js.log.Log;
import js.log.LogFactory;
import js.rmi.RmiException;
import js.util.Classes;
import js.util.Files;

/**
 * Create URL connections for both secure and not secure transactions. This connection factory deals with transport layer. Since
 * RMI transaction always uses HTTP or HTTPS for transport, connection factory creates only {@link HttpURLConnection}
 * connections.
 * <p>
 * For secure connections, takes care to set SSL context that is properly initialized with j(s)-lib certificate into trusted
 * certificates manager. It is necessary to add j(s)-lib certificate to trust manager because j(s)-lib certificate configured on
 * j(s)-lib servers is self-signed. Otherwise the client side of the connection, managed by JRE, will not recognize server side
 * as genuine.
 * <p>
 * Connection factory provides a single public method responsible for HTTP(S) connection creation with requested URL, see
 * {@link #openConnection(URL)}. It selects proper connection type, i.e. secure or not secure, based on URL protocol.
 * 
 * @author Iulian Rotaru
 * @since 1.7
 * @version draft
 */
public class ConnectionFactory {
	/** Class logger. */
	private static final Log log = LogFactory.getLog(ConnectionFactory.class);

	/** Resource path for j(s)-lib certificate authority. It is a self-signed certificate included as resource into package. */
	private static final String JS_LIB_CA_FILE = "/js-lib.crt";

	/** Alias, that is, name of the j(s)-lib certificate authority. */
	private static final String JS_LIB_CA_ALIAS = "j(s)-lib Certificate Authority";

	/**
	 * Secure connections with j(s)-lib powered servers are signed with self-signed certificate. Client needs to add it
	 * explicitly to trusted certificates manager so that secure connection managed by JRE to be accepted by client side. For
	 * connections to third part servers trust manager checks well known root certificate authorities.
	 */
	private static TrustManager[] trustManagers;
	static {
		// need to use trust manager because j(s)-lib certificate is not signed by a known authority
		// this way a secure transaction can be performed with servers possessing certificates signed by j(s)-lib
		InputStream inputStream = null;
		try {
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			keystore.load(null, null);

			inputStream = Classes.getResourceAsStream(JS_LIB_CA_FILE);
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);
			keystore.setCertificateEntry(JS_LIB_CA_ALIAS, certificate);

			TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(keystore);
			trustManagers = trustManagerFactory.getTrustManagers();
		} catch (KeyStoreException e) {
			throw new BugError("Corrupt JRE. Missing key store default type.");
		} catch (NoSuchAlgorithmException e) {
			throw new BugError("Corrupt JRE. Missing cryptography default algorithm.");
		} catch (CertificateException e) {
			throw new BugError("JRE implementation for X.509 certificate is missing or library root certificate is corrupt.");
		} catch (IOException e) {
			throw new RmiException("Fail to read library root certificate: %s", e.getMessage());
		} finally {
			Files.close(inputStream);
		}
	}

	/**
	 * Client key manager used only if this application is configured to use client certificate.
	 */
	private final KeyManager[] keyManagers;

	/**
	 * Construct the instance of the connection factory. If application is configured to use client certificate for
	 * authentication this constructor takes care to properly initialize the {@link #keyManagers key managers} repository.
	 * Otherwise it does nothing.
	 */
	public ConnectionFactory() {
		Iterator<ClientKeyStoreManager> keyStoreProvider = ServiceLoader.load(ClientKeyStoreManager.class).iterator();
		if (!keyStoreProvider.hasNext()) {
			keyManagers = null;
			return;
		}
		ClientKeyStoreManager clientKeyStoreManager = keyStoreProvider.next();
		KeyStore keyStore = clientKeyStoreManager.getKeyStore();
		String password = clientKeyStoreManager.getPassword();

		try {
			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keyStore, password.toCharArray());
			keyManagers = keyManagerFactory.getKeyManagers();
		} catch (UnrecoverableKeyException e) {
			throw new BugError("Key manager factory initialization fails due to key error. Most probably password is wrong.");
		} catch (KeyStoreException e) {
			throw new RmiException("Key manager initialization fails: %s", e);
		} catch (NoSuchAlgorithmException e) {
			throw new BugError("Corrupt JRE. Missing cryptography default algorithm.");
		}
	}

	/**
	 * Create and open a HTTP(S) connection with requested URL. URL protocol is used to detect if requested connection should be
	 * secure. For secure connections takes care to initialize SSL context.
	 * 
	 * @param url the URL to open connection with.
	 * @return HTTP connection opened with requested <code>url</code>.
	 * @throws IOException if attempt to connected remote URL fails due to networking or remote host problems,
	 * @throws NoSuchAlgorithmException if algorithms used by certificate are not supported,
	 * @throws KeyManagementException if TLS handshake fails because of invalid or expired certificates.
	 * @throws BugError if given URL protocol is not <code>http</code> or <code>https</code>.
	 */
	public HttpURLConnection openConnection(URL url) throws IOException, NoSuchAlgorithmException, KeyManagementException, BugError {
		if (!isSecure(url.getProtocol())) {
			log.debug("Open HTTP connection with |%s|.", url);
			return (HttpURLConnection) url.openConnection();
		}

		log.debug("Open %sauthenticated HTTP secure connection with |%s|.", keyManagers == null ? "not " : "", url);

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers, trustManagers, null);

		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setSSLSocketFactory(sslContext.getSocketFactory());
		return connection;
	}

	/** Standard HTTP protocol name. */
	private static final String HTTP = "http";

	/** Secure HTTP protocol name. */
	private static final String HTTPS = "https";

	/**
	 * Predicate to test if given protocol is secure.
	 * 
	 * @param protocol protocol to test if secure.
	 * @return true it given <code>protocol</code> is secure.
	 * @throws BugError if given protocol is not supported.
	 */
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
