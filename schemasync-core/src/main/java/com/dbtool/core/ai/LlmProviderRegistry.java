package com.dbtool.core.ai;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * ServiceLoader-backed registry discovering all configured {@link LlmProvider} implementations.
 */
public class LlmProviderRegistry {
    private static final LlmProviderRegistry INSTANCE = new LlmProviderRegistry();
    private final Map<LlmProviderType, LlmProvider> providers = new EnumMap<>(LlmProviderType.class);

    private LlmProviderRegistry() {
        ServiceLoader<LlmProvider> loader = ServiceLoader.load(LlmProvider.class, LlmProviderRegistry.class.getClassLoader());
        for (LlmProvider p : loader) {
            providers.put(p.getType(), p);
        }
    }

    public static LlmProviderRegistry getInstance() {
        return INSTANCE;
    }

    public LlmProvider getProvider(LlmProviderType type) {
        return type != null ? providers.get(type) : providers.get(LlmProviderType.OPENROUTER);
    }

    public Collection<LlmProvider> getAllProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }
}
