package com.sweet.market.discovery.metrics;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
@Profile("local-experiment")
public class LocalExperimentJdbcMetricsConfig {

    @Bean
    public JdbcStatementMetrics jdbcStatementMetrics(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new JdbcStatementMetrics(meterRegistry);
    }

    @Bean
    public static BeanPostProcessor instrumentDataSource(ObjectProvider<JdbcStatementMetrics> metrics) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource) || !"dataSource".equals(beanName)) {
                    return bean;
                }
                return dataSourceProxy(dataSource, metrics.getObject());
            }
        };
    }

    private static DataSource dataSourceProxy(DataSource dataSource, JdbcStatementMetrics jdbcStatementMetrics) {
        return (DataSource) Proxy.newProxyInstance(
                dataSource.getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    Object result = invoke(method, dataSource, arguments);
                    if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
                        return instrumentConnection(connection, jdbcStatementMetrics);
                    }
                    return result;
                }
        );
    }

    private static Connection instrumentConnection(Connection connection, JdbcStatementMetrics jdbcStatementMetrics) {
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    Object result = invoke(method, connection, arguments);
                    if (Statement.class.isAssignableFrom(method.getReturnType()) && result instanceof Statement statement) {
                        return instrumentStatement(statement, method.getReturnType(), jdbcStatementMetrics);
                    }
                    return result;
                }
        );
    }

    private static Object instrumentStatement(Statement statement, Class<?> statementType, JdbcStatementMetrics jdbcStatementMetrics) {
        return Proxy.newProxyInstance(
                statement.getClass().getClassLoader(),
                new Class<?>[]{statementType},
                (proxy, method, arguments) -> {
                    if (isExecution(method)) {
                        jdbcStatementMetrics.record();
                    }
                    return invoke(method, statement, arguments);
                }
        );
    }

    private static boolean isExecution(Method method) {
        return switch (method.getName()) {
            case "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch",
                 "executeLargeBatch" -> true;
            default -> false;
        };
    }

    private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        }
    }
}
