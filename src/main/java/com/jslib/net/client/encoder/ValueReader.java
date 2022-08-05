package com.jslib.net.client.encoder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Remote method returned value reader for HTTP-RMI transaction.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
public interface ValueReader {
	Object read(InputStream inputStream, Type returnType) throws IOException;
}