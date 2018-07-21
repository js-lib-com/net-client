package js.net.client.encoder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import js.dom.DocumentBuilder;
import js.util.Classes;

/**
 * XML document reader for remote method returned value.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class XmlValueReader implements ValueReader {
	@Override
	public Object read(InputStream inputStream, Type returnType) throws IOException {
		DocumentBuilder builder = Classes.loadService(DocumentBuilder.class);
		return builder.loadXML(inputStream);
	}
}