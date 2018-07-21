package js.net.client.encoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;

import js.util.Files;

/**
 * Unsupported content type on HTTP-RMI response. We reach this point for not anticipated exceptional conditions. Dump response
 * stream as string to system error using UTF-8 and returns null; of course there is the risk that dump to be not intelligible.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class UnsupportedContentTypeValueReader implements ValueReader {
	@Override
	public Object read(InputStream inputStream, Type returnType) throws IOException {
		Files.copy(new InputStreamReader(inputStream, "UTF-8"), new OutputStreamWriter(System.err, "UTF-8"));
		return null;
	}
}