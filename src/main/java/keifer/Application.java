package keifer;

import keifer.configuration.JwtFilter;
import keifer.service.model.YAMLConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableCaching
@EnableScheduling
@EnableSwagger2
@SpringBootApplication
public class Application {

    @Bean
    public FilterRegistrationBean jwtFilter(YAMLConfig yamlConfig) {
        final FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(new JwtFilter(yamlConfig));
        // Every route that reads a user's data; the sealed ones were missing, so nothing checked their tokens
        registrationBean.addUrlPatterns("/manager/*", "/sealed/*");

        return registrationBean;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
