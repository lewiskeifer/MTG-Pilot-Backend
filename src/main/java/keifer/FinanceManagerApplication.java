package keifer;

import keifer.configuration.JwtFilter;
import keifer.service.model.YAMLConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@SpringBootApplication
public class FinanceManagerApplication {

    @Bean
    public FilterRegistrationBean jwtFilter(YAMLConfig yamlConfig) {
        final FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(new JwtFilter(yamlConfig));
        registrationBean.addUrlPatterns("/manager/*");

        return registrationBean;
    }

    public static void main(String[] args) {
        SpringApplication.run(FinanceManagerApplication.class, args);
    }

}
