package keifer.configuration;

import io.swagger.annotations.Api;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.Parameter;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.SecurityConfiguration;
import springfox.documentation.swagger.web.SecurityConfigurationBuilder;

import java.util.Arrays;

import static springfox.documentation.builders.RequestHandlerSelectors.withClassAnnotation;
import static springfox.documentation.spi.DocumentationType.SWAGGER_2;

@Configuration
public class SwaggerConfig {

    Parameter headerParameter = new ParameterBuilder()
            .parameterType("header")
            .name("authorization")
            .modelRef(new ModelRef("Bearer ####"))
            .required(false)
            .description("Authorization JWT")
            .build();

    @Bean
    public Docket api() {
        return new Docket(SWAGGER_2)
                .globalOperationParameters(Arrays.asList(headerParameter))
                .select()
                .apis(withClassAnnotation(Api.class))
                .build();
    }

}
