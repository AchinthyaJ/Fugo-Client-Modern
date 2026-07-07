package io.github.fugoclient.mcefmod;

import net.montoyo.mcef.api.IJSQueryHandler;
import net.montoyo.mcef.api.IBrowser;

import net.montoyo.mcef.api.IJSQueryCallback;

public class TestJSHandler implements IJSQueryHandler {
    @Override
    public boolean onQuery(IBrowser browser, long queryId, String query, boolean persistent, IJSQueryCallback cb) {
        return false;
    }

    @Override
    public void cancelQuery(IBrowser browser, long queryId) {
    }
}
