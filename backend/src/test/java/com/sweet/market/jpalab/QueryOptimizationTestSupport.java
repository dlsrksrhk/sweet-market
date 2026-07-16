package com.sweet.market.jpalab;

import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class QueryOptimizationTestSupport extends IntegrationTestSupport {

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
        return statistics().getPrepareStatementCount();
    }

    protected long collectionFetchCount() {
        return statistics().getCollectionFetchCount();
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
