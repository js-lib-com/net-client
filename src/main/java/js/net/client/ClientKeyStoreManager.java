package js.net.client;

import java.security.KeyStore;

/**
 * Key store manager for HTTP-RMI client authentication. TOOD: This interface seems not implemented anywhere but is used by
 * {@link ConnectionFactory}.
 * 
 * @author Iulian Rotaru
 * @version draft
 */
public interface ClientKeyStoreManager {
	KeyStore getKeyStore();

	String getPassword();
}
