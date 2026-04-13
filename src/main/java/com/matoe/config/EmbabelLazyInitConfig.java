package com.matoe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Forces all Embabel-package beans to lazy-init so the application starts
 * even when no LLM provider (Ollama, LM Studio) is reachable.
 *
 * <p><b>Problem:</b> Embabel's {@code ConfigurableModelProvider} eagerly
 * discovers available models at startup. If no provider is running, the
 * bean fails with "Default LLM '…' not found in available models: []" and
 * the Spring context crashes — even though {@code TravelService} is
 * designed to fall back to a non-Embabel path.
 *
 * <p><b>Solution:</b> This {@link BeanFactoryPostProcessor} runs before any
 * beans are instantiated and marks every Embabel bean definition as lazy.
 * The beans are only created when first accessed (i.e. when
 * {@code TravelService} resolves them for the first GOAP trip). If creation
 * fails at that point, {@code TravelService} catches the exception and uses
 * the virtual-thread fallback.
 *
 * <p>This is critical for NAS/Portainer deployments where the backend
 * container may start before LM Studio / Ollama is ready.
 */
@Configuration
public class EmbabelLazyInitConfig implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmbabelLazyInitConfig.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        int count = 0;
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(name);
            String className = bd.getBeanClassName();
            // Mark beans from Embabel packages and Spring AI model packages as lazy.
            // This covers ConfigurableModelProvider, model configs, and AgentPlatform.
            if (isEmbabelBean(className, name)) {
                bd.setLazyInit(true);
                count++;
            }
        }
        if (count > 0) {
            log.info("Deferred {} Embabel/Spring-AI bean(s) to lazy-init for graceful startup", count);
        }
    }

    private static boolean isEmbabelBean(String className, String beanName) {
        if (className != null) {
            if (className.startsWith("com.embabel")) return true;
            // Spring AI beans that Embabel's model starters trigger
            if (className.startsWith("org.springframework.ai")) return true;
            if (className.contains("ModelProvider") || className.contains("ModelsConfig")) return true;
            if (className.contains("ChatModel") || className.contains("EmbeddingModel")) return true;
        }
        // Embabel-registered beans may not have a className set (e.g. @Bean methods)
        String lower = beanName.toLowerCase();
        if (lower.contains("embabel")) return true;
        if (lower.contains("agentplatform")) return true;
        if (lower.contains("modelprovider") || lower.contains("chatmodel")) return true;
        if (lower.contains("spring.ai") || lower.contains("springai")) return true;
        return false;
    }
}
