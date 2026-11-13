package com.dbtool.session;

import com.dbtool.Main;

/**
 * Listener interface for multi-connection database session lifecycle events.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public interface SessionListener {
    /**
     * Called when a new database session is connected and registered.
     */
    default void onSessionAdded(DatabaseSession session) {}

    /**
     * Called when a database session is switched to active in the workspace.
     */
    default void onSessionActivated(DatabaseSession session) {}

    /**
     * Called when a database session is closed/disconnected.
     */
    default void onSessionClosed(DatabaseSession session) {}

    /**
     * Called when a database session is detached into an independent floating window.
     */
    default void onSessionDetached(DatabaseSession session, Main detachedWindow) {}

    /**
     * Called when a detached session is re-attached back into the main workspace.
     */
    default void onSessionReattached(DatabaseSession session) {}
}
