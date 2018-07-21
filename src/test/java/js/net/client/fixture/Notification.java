package js.net.client.fixture;

import java.sql.Timestamp;

import js.lang.Event;

public class Notification implements Event {
	public int id;
	public String text;
	public Timestamp timestamp;
}