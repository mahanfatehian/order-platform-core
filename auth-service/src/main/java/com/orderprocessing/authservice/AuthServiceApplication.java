package com.orderprocessing.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication(scanBasePackages = {
        "com.orderprocessing.authservice",
        "com.orderprocessing.userservice"
} ,
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        })
@EnableJpaRepositories(basePackages = {
        "com.orderprocessing.userservice.repository"
})
@EntityScan(basePackages = {
        "com.orderprocessing.userservice.entity"
})
@ComponentScan(
        basePackages = {
                "com.orderprocessing.authservice",
                "com.orderprocessing.userservice"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.orderprocessing.userservice.config.SecurityBeansConfig.class
        )
)

public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
