package js.net.client;

import js.lang.Event;

/**
 * Ensure events connection is preserved alive. This event is sent periodically to ensure client is still alive and to keep
 * connections through routers with NAT idle connections timeout. Although TCP/IP does not require keep alive or dead connection
 * detection packets there are routers that drop idle connections after some timeout.
 * 
 * @author Iulian Rotaru
 * @since 1.6
 * @version draft
 */
public final class KeepAliveEvent implements Event
{
}
