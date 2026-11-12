package com.dbtool.core.connection;

import com.dbtool.core.model.ConnectionStatus;

public interface ConnectionEventListener {
    void onStatusChanged(ConnectionStatus oldStatus, ConnectionStatus newStatus, String message);
    void onError(Throwable throwable);
}
