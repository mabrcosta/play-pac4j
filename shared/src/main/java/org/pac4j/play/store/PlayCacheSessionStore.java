package org.pac4j.play.store;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Provider;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.play.PlayWebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.cache.SyncCacheApi;
import play.mvc.Http;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This session store internally uses the {@link PlayCacheStore} which uses the Play Cache, only an identifier is saved into the Play session.
 *
 * @author Jerome Leleu
 * @since 2.0.0
 */
@Singleton
public class PlayCacheSessionStore implements PlaySessionStore {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // prefix for the cache
    private String prefix = null;

    // store
    protected PlayCacheStore<String, Map<String, Object>> store;

    protected PlayCacheSessionStore() {}

    @Inject
    public PlayCacheSessionStore(final SyncCacheApi cache) {
        this.store = new PlayCacheStore<>(cache);
        setDefaultTimeout();
    }

    public PlayCacheSessionStore(final Provider<SyncCacheApi> cacheProvider) {
        this.store = new PlayCacheStore<>(cacheProvider);
        setDefaultTimeout();
    }

    String getPrefixedSessionKey(final String sessionId) {
        if (this.prefix != null) {
            return this.prefix + sessionId;
        } else {
            return sessionId;
        }
    }

    @Override
    public String getOrCreateSessionId(final PlayWebContext context) {
        // get current sessionId from session or from request
        String sessionId = getSessionIdFromSessionOrRequest(context);
        if (sessionId == null) {
            // generate id for session
            sessionId = java.util.UUID.randomUUID().toString();
            logger.debug("generated sessionId: {}", sessionId);
            // and save it to session/request
            setSessionIdInSession(context, sessionId);
            context.setRequestAttribute(Pac4jConstants.SESSION_ID, sessionId);
        }
        return sessionId;
    }

    protected String getSessionIdFromSessionOrRequest(final PlayWebContext context) {
        String sessionId = context.getNativeSession().getOptional(Pac4jConstants.SESSION_ID).orElse(null);
        logger.trace("retrieved sessionId from session: {}", sessionId);
        if (sessionId == null) {
            sessionId = (String) context.getRequestAttribute(Pac4jConstants.SESSION_ID).orElse(null);
            logger.trace("retrieved sessionId from request: {}", sessionId);
            // re-save it in session if defined
            if (sessionId != null) {
                logger.trace("re-saving sessionId in session: {}", sessionId);
                setSessionIdInSession(context, sessionId);
            }
        }
        return sessionId;
    }

    protected void setSessionIdInSession(final PlayWebContext context, final String sessionId) {
        context.setNativeSession(context.getNativeSession().adding(Pac4jConstants.SESSION_ID, sessionId));
    }

    @Override
    public Optional<Object> get(final PlayWebContext context, final String key) {
        final String sessionId = getOrCreateSessionId(context);
        final Optional<Map<String, Object>> values = store.get(getPrefixedSessionKey(sessionId));
        Object value = null;
        if (values != null && values.isPresent()) {
            value = values.get().get(key);
        }
        logger.trace("get, sessionId = {}, key = {} -> {}", sessionId, key, value);
        return Optional.ofNullable(value);
    }

    @Override
    public void set(final PlayWebContext context, final String key, final Object value) {
        final String sessionId = getOrCreateSessionId(context);
        String prefixedSessionKey = getPrefixedSessionKey(sessionId);
        Optional<Map<String, Object>> oldValues = store.get(prefixedSessionKey);
        Map<String, Object> values = new HashMap<>();
        if (oldValues != null && oldValues.isPresent()) {
            values = oldValues.get();
        }
        logger.trace("set, sessionId = {}, key = {}, value = {}", sessionId, key, value);
        values.put(key, value);
        store.set(prefixedSessionKey, values);
    }

    @Override
    public boolean destroySession(final PlayWebContext context) {
        final String sessionId = getSessionIdFromSessionOrRequest(context);
        if (sessionId != null) {
            context.setNativeSession(new Http.Session(new HashMap<>()));
            context.setRequestAttribute(Pac4jConstants.SESSION_ID, null);
            return true;
        }
        return false;
    }

    @Override
    public Optional<Object> getTrackableSession(final PlayWebContext context) {
        return Optional.ofNullable(getSessionIdFromSessionOrRequest(context));
    }

    @Override
    public Optional<SessionStore<PlayWebContext>> buildFromTrackableSession(final PlayWebContext context, final Object trackableSession) {
        setSessionIdInSession(context, (String) trackableSession);
        context.setRequestAttribute(Pac4jConstants.SESSION_ID, trackableSession);
        return Optional.of(this);
    }

    @Override
    public boolean renewSession(final PlayWebContext context) {
        final String oldSessionId = this.getOrCreateSessionId(context);
        final Optional<Map<String, Object>> oldData = store.get(getPrefixedSessionKey(oldSessionId));

        context.setNativeSession(context.getNativeSession().removing(Pac4jConstants.SESSION_ID));
        context.setRequestAttribute(Pac4jConstants.SESSION_ID, null);

        final String newSessionId = this.getOrCreateSessionId(context);
        if (oldData.isPresent()) {
            store.set(getPrefixedSessionKey(newSessionId), oldData.get());
        }

        logger.debug("Renewing session: {} -> {}", oldSessionId, newSessionId);
        return true;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(final String prefix) {
        this.prefix = prefix;
    }

    public int getTimeout() {
        return this.store.getTimeout();
    }

    public void setTimeout(final int timeout) {
        this.store.setTimeout(timeout);
    }

    public PlayCacheStore<String, Map<String, Object>> getStore() {
        return store;
    }

    protected void setDefaultTimeout() {
        // 1 hour = 3600 seconds
        this.store.setTimeout(3600);
    }

    @Override
    public String toString() {
        return CommonHelper.toNiceString(this.getClass(), "store", store, "prefix", prefix, "timeout", getTimeout());
    }
}
