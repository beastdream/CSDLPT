package com.example.tpcc.site1.config;

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
@EnableJpaRepositories(basePackages = "com.example.tpcc.site1.repo", // Chỉ quét repo của Site 1
        entityManagerFactoryRef = "site1EntityManager", transactionManagerRef = "site1TransactionManager")
public class Site1Config {

    // @Primary // Đánh dấu đây là DB chính
    @Bean(name = "site1DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.site1")
    public DataSource site1DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "site1EntityManager")
    public LocalContainerEntityManagerFactoryBean site1EntityManager() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(site1DataSource());
        em.setPackagesToScan("com.example.tpcc.site1.entity"); // Chỉ quét entity của Site 1
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none"); // Vì ta đã tạo bảng sẵn
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        em.setJpaPropertyMap(properties);
        return em;
    }

    @Bean(name = "site1TransactionManager")
    public PlatformTransactionManager site1TransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(site1EntityManager().getObject());
        return transactionManager;
    }
}