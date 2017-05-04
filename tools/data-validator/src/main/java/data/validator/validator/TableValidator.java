package data.validator.validator;

import data.validator.DataValidator;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import org.springframework.context.ApplicationContext;

/**
 * Created by Tarek on 5/2/2017.
 */
public class TableValidator implements Validator {
    @Override
    public void validate(CassandraConnectionProvider sourceCassandraConnectionProvider, CassandraConnectionProvider targetCassandraConnectionProvider, String sourceTableName, String targetTableName, int threadsCount) {
        System.out.println("Checking data integrity between source table " + sourceTableName + " and target table " + targetTableName);
        DataValidator dataValidator = new DataValidator(sourceCassandraConnectionProvider, targetCassandraConnectionProvider);
        dataValidator.validate(sourceTableName, targetTableName, threadsCount);
    }
}