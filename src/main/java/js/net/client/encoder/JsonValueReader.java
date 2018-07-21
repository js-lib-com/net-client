package js.net.client.encoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import js.json.Json;
import js.util.Classes;
import js.util.Files;

/**
 * JSON reader for remote method returned value.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class JsonValueReader implements ValueReader {
	private final Json json;

	public JsonValueReader() {
		this.json = Classes.loadService(Json.class);
	}

	@Override
	public Object read(InputStream inputStream, Type returnType) throws IOException {
		BufferedReader reader = null;
		try {
			reader = Files.createBufferedReader(inputStream);
			return json.parse(reader, returnType);
		} finally {
			Files.close(reader);
		}
	}
}