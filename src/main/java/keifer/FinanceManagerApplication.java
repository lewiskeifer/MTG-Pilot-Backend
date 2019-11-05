package keifer;

import keifer.configuration.JwtFilter;
import keifer.service.model.YAMLConfig;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableScheduling
@EnableSwagger2
@SpringBootApplication
public class FinanceManagerApplication {

    private static ConfigurableApplicationContext context;

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

    // Restart weekly
    @Scheduled(initialDelay = 604800000, fixedRate = 604800000)
    public static void restart() {
        ApplicationArguments args = context.getBean(ApplicationArguments.class);

        Thread thread = new Thread(() -> {
            context.close();
            context = SpringApplication.run(FinanceManagerApplication.class, args.getSourceArgs());
        });

        thread.setDaemon(false);
        thread.start();
    }

}
