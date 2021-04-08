package eu.europeana.cloud.service.dps.storm.utils;

import com.google.common.collect.Streams;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.test.CassandraTestInstance;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static eu.europeana.cloud.service.dps.storm.utils.HarvestedRecord.builder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HarvestedRecordsDAOTest extends CassandraTestBase {

    private static final String METIS_DATASET_ID = "114411";
    private static final String OAI_ID_1 = "http://data.europeana.eu/item/2058621/LoCloud_census_1891_00037ace_1df9_438f_96eb_ea37bc646ec9";
    private static final String OAI_ID_2 = "http://data.europeana.eu/item/2058621/object_NRA_9857684";
    private static final String OAI_ID_3 = "http://data.europeana.eu/item/2058621/LoCloud_census_1891_008283c8_8ed6_49ab_9082_44ab4317d62a";
    private static final Date HARVESTED_DATE = new Date(0);
    private static final Date INDEXING_DATE = new Date(1000);
    private static final UUID MD5 = UUID.randomUUID();
    private HarvestedRecordsDAO dao;

    @Before
    public void setup() {
        CassandraConnectionProvider db = new CassandraConnectionProvider(HOST, CassandraTestInstance.getPort(), KEYSPACE, USER, PASSWORD);
        dao = new HarvestedRecordsDAO(db);
    }

    @Test
    public void shouldFindInsertedRecords() {
        dao.insertHarvestedRecord(builder().metisDatasetId(METIS_DATASET_ID).recordLocalId(OAI_ID_1)
                .harvestDate(HARVESTED_DATE).build());
        dao.insertHarvestedRecord(builder().metisDatasetId(METIS_DATASET_ID).recordLocalId(OAI_ID_2)
                .harvestDate(HARVESTED_DATE).indexingDate(INDEXING_DATE).md5(MD5).build());


        HarvestedRecord record1 = dao.findRecord(METIS_DATASET_ID, OAI_ID_1).orElseThrow();
        HarvestedRecord record2 = dao.findRecord(METIS_DATASET_ID, OAI_ID_2).orElseThrow();


        assertEquals(METIS_DATASET_ID, record1.getMetisDatasetId());
        assertEquals(OAI_ID_1, record1.getRecordLocalId());
        assertEquals(HARVESTED_DATE, record1.getHarvestDate());
        assertNull(record1.getIndexingDate());
        assertNull(record1.getMd5());

        assertEquals(METIS_DATASET_ID, record2.getMetisDatasetId());
        assertEquals(OAI_ID_2, record2.getRecordLocalId());
        assertEquals(HARVESTED_DATE, record2.getHarvestDate());
        assertEquals(INDEXING_DATE, record2.getIndexingDate());
        assertEquals(MD5, record2.getMd5());

    }

    @Test
    public void shouldReturnEmptyOptionalWhenNoRecordFound() {

        Optional<HarvestedRecord> record = dao.findRecord(METIS_DATASET_ID, OAI_ID_3);

        assertTrue(record.isEmpty());
    }

    @Test
    public void shouldIterateThrowDatasetRecords() {
        dao.insertHarvestedRecord(builder().metisDatasetId(METIS_DATASET_ID).recordLocalId(OAI_ID_1).harvestDate(HARVESTED_DATE).build());
        dao.insertHarvestedRecord(builder().metisDatasetId(METIS_DATASET_ID).recordLocalId(OAI_ID_2).harvestDate(HARVESTED_DATE).build());

        Iterator<HarvestedRecord> iterator = dao.findDatasetRecords(METIS_DATASET_ID);

        List<HarvestedRecord> result = Streams.stream(iterator).collect(Collectors.toList());

        assertEquals(2, result.size());
        assertEquals(1, result.stream().filter(record -> record.getRecordLocalId().equals(OAI_ID_1)).count());
        assertEquals(1, result.stream().filter(record -> record.getRecordLocalId().equals(OAI_ID_2)).count());
    }

}