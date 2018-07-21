package js.net.client;

import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestsSuite extends TestCase {
	public static TestSuite suite() {
		TestSuite suite = new TestSuite();
		suite.addTestSuite(EventReaderUnitTest.class);
		suite.addTestSuite(EncodersUnitTest.class);
		suite.addTestSuite(HttpRmiTransactionUnitTest.class);
		suite.addTestSuite(HttpRmiTransactionHandlerUnitTest.class);
		suite.addTestSuite(HttpRmiTransactionProxyUnitTest.class);
		return suite;
	}
}
