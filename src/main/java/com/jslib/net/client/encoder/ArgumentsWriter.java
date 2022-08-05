package com.jslib.net.client.encoder;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Remote method arguments writer for client HTTP-RMI requests.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
public interface ArgumentsWriter {
	boolean isSynchronous();

	/**
	 * Get content type choose by factory method to encode arguments.
	 * 
	 * @return encoder content type.
	 */
	String getContentType();

	/**
	 * Write arguments to output stream.
	 * 
	 * @param outputStream output stream to write arguments to,
	 * @param arguments arguments list.
	 * @throws IOException if output stream write fails.
	 */
	void write(OutputStream outputStream, Object[] arguments) throws IOException;
}