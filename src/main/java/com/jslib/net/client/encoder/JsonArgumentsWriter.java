package com.jslib.net.client.encoder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;

import com.jslib.api.json.Json;
import com.jslib.util.Classes;
import com.jslib.util.Files;

/**
 * Client side JSON writer for remote method parameters.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class JsonArgumentsWriter implements ArgumentsWriter {
	private final Json json;

	public JsonArgumentsWriter() {
		this.json = Classes.loadService(Json.class);
	}

	@Override
	public String getContentType() {
		return "application/json";
	}

	@Override
	public void write(OutputStream outputStream, Object[] arguments) throws IOException {
		BufferedWriter writer = null;
		try {
			writer = Files.createBufferedWriter(outputStream);
			json.stringify(writer, arguments);
		} finally {
			Files.close(writer);
		}
	}
}