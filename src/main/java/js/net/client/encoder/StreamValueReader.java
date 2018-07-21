package js.net.client.encoder;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.jar.JarInputStream;
import java.util.zip.ZipInputStream;

import js.io.FilesInputStream;
import js.util.Params;

/**
 * Client side stream reader for remote method returned value.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class StreamValueReader implements ValueReader {
	@Override
	public Object read(InputStream inputStream, Type returnType) throws IOException {
		return getInstance(inputStream, returnType);
	}

	/**
	 * Create stream of requested type wrapping given input stream. Both returned bytes and character streams are closeable.
	 * 
	 * @param inputStream input stream to wrap,
	 * @param type the type of stream, byte or character.
	 * @return newly create stream.
	 * @throws IOException if newly stream creation fails.
	 */
	public static Closeable getInstance(InputStream inputStream, Type type) throws IOException {
		Params.notNull(inputStream, "Input stream");

		// TODO: construct instance reflexively to allow for user defined input stream
		// an user defined input stream should have a constructor with a single parameter of type InputStream

		if (type == InputStream.class) {
			return inputStream;
		}
		if (type == ZipInputStream.class) {
			return new ZipInputStream(inputStream);
		}
		if (type == JarInputStream.class) {
			return new JarInputStream(inputStream);
		}
		if (type == FilesInputStream.class) {
			return new FilesInputStream(inputStream);
		}
		if (type == Reader.class || type == InputStreamReader.class) {
			return new InputStreamReader(inputStream, "UTF-8");
		}
		if (type == BufferedReader.class) {
			return new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
		}
		if (type == LineNumberReader.class) {
			return new LineNumberReader(new InputStreamReader(inputStream, "UTF-8"));
		}
		if (type == PushbackReader.class) {
			return new PushbackReader(new InputStreamReader(inputStream, "UTF-8"));
		}
		throw new IllegalArgumentException(String.format("Unsupported stream type |%s|.", type));
	}
}