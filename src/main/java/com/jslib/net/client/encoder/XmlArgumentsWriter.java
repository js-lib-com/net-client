package com.jslib.net.client.encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.jslib.api.dom.Document;

/**
 * XML document writer for remote method arguments.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
final class XmlArgumentsWriter implements ArgumentsWriter {
	@Override
	public boolean isSynchronous() {
		return false;
	}

	@Override
	public String getContentType() {
		return "text/xml; charset=UTF-8";
	}

	@Override
	public void write(OutputStream outputStream, Object[] arguments) throws IOException {
		final Document document = (Document) arguments[0];
		document.serialize(new OutputStreamWriter(outputStream, "UTF-8"));
	}
}