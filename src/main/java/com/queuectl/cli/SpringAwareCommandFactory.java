package com.queuectl.cli;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Lets picocli instantiate command classes as Spring beans (constructor injection works),
 * falling back to picocli's default factory for anything not managed by Spring.
 */
@Component
public class SpringAwareCommandFactory implements CommandLine.IFactory {

    private final ApplicationContext context;

    public SpringAwareCommandFactory(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        try {
            return context.getBean(cls);
        } catch (NoSuchBeanDefinitionException e) {
            return CommandLine.defaultFactory().create(cls);
        }
    }
}
