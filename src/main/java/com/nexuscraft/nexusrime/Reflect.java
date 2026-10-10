package com.nexuscraft.nexusrime;

import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

/** Small calls that exist on some server builds but not others, made safely. */
final class Reflect {

    private Reflect() {
    }

    /** Stops a skeleton or zombie from catching fire in daylight, when the server supports it. A
     *  server without the method simply leaves the default, which is harmless. */
    static void noSunBurn(Entity entity) {
        try {
            for (Method m : entity.getClass().getMethods()) {
                if (m.getName().equals("setShouldBurnInDay") && m.getParameterCount() == 1) {
                    m.invoke(entity, false);
                    return;
                }
            }
        } catch (Throwable unsupported) {
            // not available on this build -- fine
        }
    }
}
