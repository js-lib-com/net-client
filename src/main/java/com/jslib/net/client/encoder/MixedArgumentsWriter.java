package com.jslib.net.client.encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import com.jslib.api.dom.Document;
import com.jslib.io.StreamHandler;
import com.jslib.api.json.Json;
import com.jslib.util.Classes;
import com.jslib.util.Files;
import com.jslib.util.Params;
import com.jslib.util.Strings;

/**
 * Client side multipart mixed writer for remote method arguments. Content type is
 * <code>multipart/mixed; boundary="${boundary}"</code> where <code>boundary</code> variable is a random string less than 70
 * characters length using ASCII letters, digits, space and couple punctuation marks, see below syntax description. This class
 * implementation uses {@link Strings#UUID()}.
 * 
 * <pre>
 * boundary = *69( chars ) chars-nospace 
 * chars = chars-nospace / " " 
 * chars-nospace = ALPHA / DIGIT / "'" / "(" / ")" / "+" / "_" / "," / "-" / "." / "/" / ":" / "=" / "?" 
 * 
 * multipart-body = 1*encapsulation close-delimiter
 * encapsulation = delimiter CRLF body-part
 * delimiter = CRLF "--" boundary ; boundary taken from Content-Type field
 * close-delimiter = delimiter "--"
 * </pre>
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class MixedArgumentsWriter implements ArgumentsWriter {
	private static final Charset ASCII = Charset.forName("ASCII");
	private static final byte[] FIELD_SEP = ASCII.encode(": ").array();
	private static final byte[] CRLF = ASCII.encode("\r\n").array();
	private static final byte[] TWO_DASHES = ASCII.encode("--").array();

	private final Json json;
	private final String boundary = Strings.UUID();
	private OutputStream outputStream;
	private int partIndex;

	public MixedArgumentsWriter() {
		this.json = Classes.loadService(Json.class);
	}

	@Override
	public String getContentType() {
		return String.format("multipart/mixed; boundary=\"%s\"", this.boundary);
	}

	@Override
	public void write(OutputStream outputStream, Object[] arguments) throws IOException {
		Params.notNull(outputStream, "Output stream");
		this.outputStream = outputStream;
		try {
			for (Object argument : arguments) {
				delimiter();
				crlf();
				if (argument instanceof StreamHandler<?>) {
					write((StreamHandler<?>) argument);
				} else if (argument instanceof Document) {
					write((Document) argument);
				} else {
					write(argument);
				}
			}
			closeDelimiter();
		} finally {
			outputStream.close();
		}
	}

	private void write(Object object) throws IOException {
		header("application/json");
		// is not necessary to flush writer since JSON serializer guarantee it
		json.stringify(Files.createBufferedWriter(outputStream), object);
	}

	private void write(StreamHandler<?> streamHandler) throws IOException {
		header("application/octet-stream");
		// disable stream close because we need to write close delimiter after stream content
		streamHandler.invokeHandler(new UncloseableOutputStream(outputStream));
	}

	private void write(Document document) throws IOException {
		header("text/xml; charset=UTF-8");
		document.serialize(new OutputStreamWriter(outputStream, "UTF-8"));
	}

	private void header(String contentType) throws IOException {
		// add Content-Disposition required by Apache fileupload library
		// it seems the same library requires 'form-data'; tests with 'inline' or 'attachement' was failing
		// also 'name' parameter is mandatory and use it to send parameter index, for now not used on server
		field("Content-Disposition", String.format("form-data; name=\"%d\"", partIndex++));
		field("Content-Type", contentType);
		crlf();
	}

	private void field(String name, String value) throws IOException {
		outputStream.write(ASCII.encode(name).array());
		outputStream.write(FIELD_SEP);
		outputStream.write(ASCII.encode(value).array());
		outputStream.write(CRLF);
	}

	private void delimiter() throws IOException {
		outputStream.write(CRLF);
		outputStream.write(TWO_DASHES);
		outputStream.write(ASCII.encode(boundary).array());
	}

	/**
	 * Add two dashes to mark part delimiter close.
	 * 
	 * @throws IOException if output stream write fails.
	 */
	private void closeDelimiter() throws IOException {
		delimiter();
		outputStream.write(TWO_DASHES);
	}

	/**
	 * Write CR-LF pair to signal end of line.
	 * 
	 * @throws IOException if output stream write fails.
	 */
	private void crlf() throws IOException {
		outputStream.write(CRLF);
	}
}