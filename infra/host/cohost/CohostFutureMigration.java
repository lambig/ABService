import org.flywaydb.core.Flyway;

/** Disposable runtime fixture: use the production jar's Flyway and JDBC driver. */
public class CohostFutureMigration {
    public static void main(String[] args) {
        Flyway.configure()
                .dataSource("jdbc:postgresql://postgres:5432/" + System.getenv("DB_NAME"),
                        System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))
                .locations("classpath:db/migration", "filesystem:/migration-fixture/sql")
                .load().migrate();
    }
}
