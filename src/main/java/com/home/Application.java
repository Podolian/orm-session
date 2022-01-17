package com.home;

import com.home.domain.Hit;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;


public class Application {
    public static void main(String[] args) {
        var dataSource = initDatasource();
        Session session = new SessionFactory(dataSource).createSession();

        checkFind(session);
        checkUpdate(session);

        session.close();
    }

    private static void checkUpdate(Session session) {
        Hit hit = session.find(Hit.class, 2L);
        hit.setSound("boom");
    }

    private static void checkFind(Session session) {
        Hit hit = session.find(Hit.class, 1L);
        System.out.println(hit);
        Hit anotherHit = session.find(Hit.class, 2L);
        System.out.println(anotherHit);

        System.out.println(hit == anotherHit);
    }

    private static DataSource initDatasource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:5432/postgres");
        dataSource.setUser("dp");
        dataSource.setPassword("andersen");
        return dataSource;
    }
}
