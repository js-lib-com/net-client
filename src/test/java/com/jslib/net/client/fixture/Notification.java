package com.jslib.net.client.fixture;

import java.sql.Timestamp;

import com.jslib.lang.Event;

public class Notification implements Event {
	public int id;
	public String text;
	public Timestamp timestamp;
}