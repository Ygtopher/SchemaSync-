package com.dbtool.session;

import com.dbtool.DatabaseManager;
import com.dbtool.Main;

import javax.swing.SwingUtilities;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry and lifecycle coordinator for multi-connection database sessions in SchemaSync.
 * Supports running multiple database connections simultaneously, switching between them with 0ms latency,
 * and detaching/re-attaching database workspaces across windows and monitors.
 *
 * Created & Developed by: NSENGIYUMVA Christophe
 */
public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    private final List<DatabaseSession> sessions = new CopyOnWriteArrayList<>();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile DatabaseSession activeSession = null;
    private volatile Main primaryMainWindow = null;

    private SessionManager() {}

    public void setPrimaryMainWindow(Main mainWindow) {
        this.primaryMainWindow = mainWindow;
    }

    public Main getPrimaryMainWindow() {
        return primaryMainWindow;
    }

    public void addListener(SessionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    public synchronized DatabaseSession registerSession(String name, String type, DatabaseManager dbManager) {
        DatabaseSession session = new DatabaseSession(name, type, dbManager);
        return registerSession(session);
    }

    public synchronized DatabaseSession registerSession(DatabaseSession session) {
        if (session == null) return null;
        if (!sessions.contains(session)) {
            sessions.add(session);
        }
        if (activeSession == null) {
            activeSession = session;
        }

        notifyListeners(l -> l.onSessionAdded(session));
        if (activeSession == session) {
            notifyListeners(l -> l.onSessionActivated(session));
        }
        return session;
    }

    public synchronized void setActiveSession(DatabaseSession session) {
        if (session != null && sessions.contains(session)) {
            this.activeSession = session;
            notifyListeners(l -> l.onSessionActivated(session));
        }
    }

    public synchronized void closeSession(DatabaseSession session) {
        if (session == null) return;
        boolean wasActive = (activeSession == session);
        session.close();
        sessions.remove(session);

        if (wasActive) {
            // Find another attached session to activate
            DatabaseSession next = null;
            for (DatabaseSession s : sessions) {
                if (!s.isDetached()) {
                    next = s;
                    break;
                }
            }
            if (next == null && !sessions.isEmpty()) {
                next = sessions.get(0);
            }
            activeSession = next;
        }

        notifyListeners(l -> l.onSessionClosed(session));
        if (wasActive && activeSession != null) {
            notifyListeners(l -> l.onSessionActivated(activeSession));
        }
    }

    public synchronized void detachSession(DatabaseSession session, Main parentWindow) {
        if (session == null || session.isDetached()) return;

        session.setDetached(true);
        Main detachedWindow = new Main(session, parentWindow != null ? parentWindow : primaryMainWindow);
        session.setDetachedWindow(detachedWindow);

        if (parentWindow != null) {
            java.awt.Point loc = parentWindow.getLocation();
            detachedWindow.setLocation(loc.x + 40, loc.y + 40);
        } else if (primaryMainWindow != null) {
            java.awt.Point loc = primaryMainWindow.getLocation();
            detachedWindow.setLocation(loc.x + 40, loc.y + 40);
        } else {
            detachedWindow.setLocationRelativeTo(null);
        }
        detachedWindow.setVisible(true);

        // If this was the active session in main window, select another attached session
        if (activeSession == session) {
            DatabaseSession nextAttached = null;
            for (DatabaseSession s : sessions) {
                if (!s.isDetached()) {
                    nextAttached = s;
                    break;
                }
            }
            activeSession = nextAttached;
        }

        notifyListeners(l -> l.onSessionDetached(session, detachedWindow));
        if (activeSession != null) {
            notifyListeners(l -> l.onSessionActivated(activeSession));
        }
    }

    public synchronized void reattachSession(DatabaseSession session) {
        if (session == null || !session.isDetached()) return;

        Main detachedWin = session.getDetachedWindow();
        if (detachedWin != null) {
            try {
                detachedWin.dispose();
            } catch (Exception ignored) {}
            session.setDetachedWindow(null);
        }

        session.setDetached(false);
        activeSession = session;

        if (primaryMainWindow != null) {
            primaryMainWindow.toFront();
            primaryMainWindow.requestFocus();
        }

        notifyListeners(l -> l.onSessionReattached(session));
        notifyListeners(l -> l.onSessionActivated(session));
    }

    public List<DatabaseSession> getAllSessions() {
        return Collections.unmodifiableList(sessions);
    }

    public List<DatabaseSession> getAttachedSessions() {
        List<DatabaseSession> list = new ArrayList<>();
        for (DatabaseSession s : sessions) {
            if (!s.isDetached()) list.add(s);
        }
        return list;
    }

    public DatabaseSession getActiveSession() {
        return activeSession;
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public synchronized void closeAllSessions() {
        for (DatabaseSession session : sessions) {
            try {
                session.close();
            } catch (Exception ignored) {}
        }
        sessions.clear();
        activeSession = null;
    }

    private void notifyListeners(java.util.function.Consumer<SessionListener> action) {
        for (SessionListener l : listeners) {
            try { action.accept(l); } catch (Exception ignored) {}
        }
    }
}
