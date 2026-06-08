package com.example.tpcc.site2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@EnableJpaRepositories(basePackages = "com.example.tpcc.site2.repo", // Chỉ quét repo của Site 2
        entityManagerFactoryRef = "site2EntityManager", transactionManagerRef = "site2TransactionManager")
public class Site2Config {

    @Primary // Đánh dấu đây là DB chính
    @Bean(name = "site2DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.site2")
    public DataSource site2DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "site2EntityManager")
    public LocalContainerEntityManagerFactoryBean site2EntityManager() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(site2DataSource());
        em.setPackagesToScan("com.example.tpcc.site2.entity"); // Chỉ quét entity của Site 2
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none"); // Vì ta đã tạo bảng sẵn
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        em.setJpaPropertyMap(properties);
        return em;
    }

    @Bean(name = "site2TransactionManager")
    public PlatformTransactionManager site2TransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(site2EntityManager().getObject());
        return transactionManager;
    }
}