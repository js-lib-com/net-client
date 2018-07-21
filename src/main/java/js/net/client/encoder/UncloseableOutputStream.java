package js.net.client.encoder;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream with close operation disabled.
 * 
 * @author Iulian Rotaru
 * @since 1.8
 * @version draft
 */
class UncloseableOutputStream extends OutputStream {
	private OutputStream outputStream;

	public UncloseableOutputStream(OutputStream outputStream) {
		this.outputStream = outputStream;
	}

	@Override
	public void write(int b) throws IOException {
		outputStream.write(b);
	}

	@Override
	public void close() throws IOException {
		outputStream.flush();
	}
}