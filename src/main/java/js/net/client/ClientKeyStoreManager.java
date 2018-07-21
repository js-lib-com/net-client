package js.net.client;

import java.security.KeyStore;

/**
 * Key store manager for HTTP-RMI client authentication. TOOD: This interface seems not implemented anywhere but is used by
 * {@link ConnectionFactory}.
 * 
 * @author Iulian Rotaru
 * @since 1.6
 * @version draft
 */
public interface ClientKeyStoreManager {
	KeyStore getKeyStore();

	String getPassword();
}
