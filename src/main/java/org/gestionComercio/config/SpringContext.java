package org.gestionComercio.config;

import org.springframework.context.ConfigurableApplicationContext;

public class SpringContext {

    private static ConfigurableApplicationContext context;

    private SpringContext() {
    }

    public static void setContext(ConfigurableApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static ConfigurableApplicationContext getContext() {
        return context;
    }
}