package ru.spbstu.hsai.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

@Configuration
@PropertySource("classpath:application.properties")
@ComponentScan({"ru.spbstu.hsai"})
public class VaultConfig implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Создаем VaultTemplate вручную, так как автовайринг не работает здесь
        ConfigurableEnvironment environment = (ConfigurableEnvironment) beanFactory.getBean(Environment.class);
        VaultTemplate vaultTemplate = beanFactory.getBean(VaultTemplate.class);

        String vaultPath = "secret/data/currency-converter-bot";
        VaultResponse response = vaultTemplate.read(vaultPath);

        if (response != null && response.getData() != null) {
            Map<String, Object> secrets = (Map<String, Object>) response.getData().get("data");
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("vault-secrets", secrets));
        }
    }

    @Bean
    public static VaultTemplate vaultTemplate(Environment env) {
        String uri = env.getProperty("spring.cloud.vault.uri");
        int port = env.getProperty("spring.cloud.vault.port", Integer.class, 8200);
        String token = env.getProperty("spring.cloud.vault.token");
        String scheme = env.getProperty("spring.cloud.vault.scheme", "http");

        VaultEndpoint endpoint = VaultEndpoint.create(uri, port);
        endpoint.setScheme(scheme);
        return new VaultTemplate(endpoint, new TokenAuthentication(token));
    }

}