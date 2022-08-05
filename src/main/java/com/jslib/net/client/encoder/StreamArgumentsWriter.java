package com.jslib.net.client.encoder;

import java.io.IOException;
import java.io.OutputStream;

import com.jslib.io.StreamHandler;
import com.jslib.util.Files;

/**
 * Client side bytes stream writer for remote method arguments.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class StreamArgumentsWriter implements ArgumentsWriter {
	@Override
	public boolean isSynchronous() {
		return true;
	}

	@Override
	public String getContentType() {
		return "application/octet-stream";
	}

	@Override
	public void write(OutputStream outputStream, Object[] arguments) throws IOException {
		// do not check parameters argument length and instance type because is already checked by encoder factory
		try {
			StreamHandler<?> streamHandler = (StreamHandler<?>) arguments[0];
			streamHandler.invokeHandler(outputStream);
		} finally {
			Files.close(outputStream);
		}
	}
}