//package io.github.jesse0722.speedkill.config;
//
//import com.zaxxer.hikari.HikariConfig;
//import com.zaxxer.hikari.HikariDataSource;
//import io.github.jesse0722.speedkill.mysql.DynamicDataSource;
//import lombok.Data;
//import org.apache.ibatis.session.SqlSessionFactory;
//import org.mybatis.spring.SqlSessionFactoryBean;
//import org.mybatis.spring.SqlSessionTemplate;
//import org.mybatis.spring.annotation.MapperScan;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//import org.springframework.boot.jdbc.DataSourceBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.annotation.Order;
//import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
//import org.springframework.jdbc.datasource.DataSourceTransactionManager;
//import org.springframework.stereotype.Component;
//
//import javax.sql.DataSource;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * @author Lijiajun
// * @date 2020/11/29 21:43
// */
//
////@Configuration
//@MapperScan(basePackages = "io.github.jesse0722.speedkill.dao", sqlSessionTemplateRef = "sqlTemplate")
//public class DataSourceConfig {
//
//    @Autowired
//    private MasterJdbcProperties masterJdbcProperties;
//
//    @Autowired
//    private Slave1JdbcProperties slave1JdbcProperties;
//
//    @Bean
//    public DataSource master() {
//        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl(masterJdbcProperties.getUrl());
//        config.setUsername(masterJdbcProperties.getUsername());
//        config.setPassword(masterJdbcProperties.getPassword());
//        config.setDriverClassName(masterJdbcProperties.driverClassName);
//        return new HikariDataSource(config);
//    }
//
//    @Bean
//    public DataSource slave1() {
//        HikariConfig config = new HikariConfig();
//        config.setJdbcUrl(slave1JdbcProperties.getUrl());
//        config.setUsername(slave1JdbcProperties.getUsername());
//        config.setPassword(slave1JdbcProperties.getPassword());
//        config.setDriverClassName(slave1JdbcProperties.driverClassName);
//        return new HikariDataSource(config);
//    }
//
//    @Bean
//    public DynamicDataSource dynamicDS(@Qualifier("master") DataSource masterDS, @Qualifier("slave1") DataSource slave1DS) {
//        DynamicDataSource dynamicDataSource = new DynamicDataSource();
//        Map<Object, Object> targetDataSources = new HashMap<>();
//        targetDataSources.putIfAbsent("master", masterDS);
//        targetDataSources.putIfAbsent("slave1", slave1DS);
//        dynamicDataSource.setTargetDataSources(targetDataSources);
//        dynamicDataSource.setDefaultTargetDataSource(masterDS);
//        return dynamicDataSource;
//    }
//
//
//
//    @Component
//    @ConfigurationProperties(prefix = "spring.datasource.master")
//    @Data
//    private class MasterJdbcProperties {
//        private String url;
//        private String username;
//        private String password;
//        private String driverClassName;
//    }
//
//    @Component
//    @ConfigurationProperties(prefix = "spring.datasource.slave1")
//    @Data
//    private class Slave1JdbcProperties {
//        private String url;
//        private String username;
//        private String password;
//        private String driverClassName;
//    }
//
//    @Bean
//    public SqlSessionFactory sqlSessionFactory(@Qualifier("dynamicDS") DataSource dataSource) throws Exception {
//        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
//        bean.setDataSource(dataSource);
//        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
//        return bean.getObject();
//    }
//
//    @Bean
//    public SqlSessionTemplate sqlTemplate(@Qualifier("sqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
//        return new SqlSessionTemplate(sqlSessionFactory);
//    }
//
//    @Bean
//    public DataSourceTransactionManager dataSourceTx(@Qualifier("dynamicDS") DataSource dynamicDataSource) {
//        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
//        dataSourceTransactionManager.setDataSource(dynamicDataSource);
//        return dataSourceTransactionManager;
//    }
//}
