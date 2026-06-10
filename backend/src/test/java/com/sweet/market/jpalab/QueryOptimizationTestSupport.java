package com.sweet.market.jpalab;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;

import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

abstract class QueryOptimizationTestSupport extends IntegrationTestSupport {

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    protected void resetStatistics() {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    protected long queryCount() {
        Statistics statistics = statistics();
        return statistics.getPrepareStatementCount() + statistics.getEntityFetchCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
