package com.railway.apigateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webflux.ui.SwaggerIndexPageTransformer;
import org.springdoc.webflux.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.resource.ResourceTransformerChain;
import org.springframework.web.reactive.resource.TransformedResource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Extend Springdoc's generated initializer without replacing its service dropdown/config. */
@Component
public class SwaggerSessionTransformer extends SwaggerIndexPageTransformer {
    private final String lifecycleScript;

    public SwaggerSessionTransformer(SwaggerUiConfigProperties config, SwaggerUiOAuthProperties oauth,
            SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) throws IOException {
        super(config, oauth, welcome, mapper);
        lifecycleScript = new ClassPathResource("swagger/session-lifecycle.js")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<Resource> transform(ServerWebExchange exchange, Resource resource, ResourceTransformerChain chain) {
        return super.transform(exchange, resource, chain).map(transformed -> {
            if (!"swagger-initializer.js".equals(resource.getFilename())) return transformed;
            try {
                exchange.getResponse().getHeaders().setCacheControl("no-store");
                String initializer = transformed.getContentAsString(StandardCharsets.UTF_8);
                return new TransformedResource(resource, (initializer + "\n" + lifecycleScript)
                        .getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Could not initialize Swagger session controls", e);
            }
        });
    }
}
