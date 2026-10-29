package com.github.relativobr.supreme.diagnostics;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class LegacyProviderRegistrationTest {

  interface Provider {
    String name();
  }

  @Test
  void acceptsTypedProviderWithoutChangingPriorityOrOwner() {
    ServicesManager services = mock(ServicesManager.class);
    Plugin owner = mock(Plugin.class);
    Provider provider = () -> "Supreme";
    LegacyProviderRegistration.register(services, Provider.class, provider, owner);
    verify(services).register(Provider.class, provider, owner, ServicePriority.Normal);
  }

  @Test
  void acceptsProxyDiscoveredUsingTheRuntimeClassToken() throws ClassNotFoundException {
    ServicesManager services = mock(ServicesManager.class);
    Plugin owner = mock(Plugin.class);
    Class<?> discovered = Class.forName(Provider.class.getName(), false, Provider.class.getClassLoader());
    Object proxy = Proxy.newProxyInstance(discovered.getClassLoader(), new Class<?>[] {discovered},
        (self, method, arguments) -> switch (method.getName()) {
          case "name", "toString" -> "Supreme";
          case "hashCode" -> System.identityHashCode(self);
          case "equals" -> arguments != null && arguments.length == 1 && self == arguments[0];
          default -> null;
        });

    LegacyProviderRegistration.register(services, discovered, proxy, owner);
    verify(services).register(Provider.class, Provider.class.cast(proxy), owner, ServicePriority.Normal);
  }

  @Test
  void rejectsMismatchedProviderBeforeMutatingTheServicesManager() {
    ServicesManager services = mock(ServicesManager.class);
    Plugin owner = mock(Plugin.class);
    assertThrows(ClassCastException.class,
        () -> LegacyProviderRegistration.register(services, Provider.class, new Object(), owner));
    verifyNoInteractions(services);
  }
}
