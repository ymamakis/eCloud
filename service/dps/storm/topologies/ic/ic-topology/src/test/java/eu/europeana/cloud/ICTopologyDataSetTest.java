package eu.europeana.cloud;


import backtype.storm.Config;
import backtype.storm.ILocalCluster;
import backtype.storm.Testing;
import backtype.storm.generated.StormTopology;
import backtype.storm.testing.CompleteTopologyParam;
import backtype.storm.testing.MkClusterParam;
import backtype.storm.testing.MockedSources;
import backtype.storm.testing.TestJob;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;
import eu.europeana.cloud.bolts.TestSpout;
import eu.europeana.cloud.common.model.File;
import eu.europeana.cloud.common.model.Representation;

import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.storm.*;
import eu.europeana.cloud.service.dps.storm.io.ReadDatasetBolt;
import eu.europeana.cloud.service.dps.storm.io.ReadFileBolt;
import eu.europeana.cloud.service.dps.storm.io.WriteRecordBolt;
import eu.europeana.cloud.service.dps.storm.topologies.ic.converter.exceptions.ICSException;
import eu.europeana.cloud.service.dps.storm.topologies.ic.topology.bolt.IcBolt;
import eu.europeana.cloud.service.dps.storm.utils.TestConstantsHelper;
import eu.europeana.cloud.service.mcs.exception.MCSException;

import org.apache.tika.mime.MimeTypeException;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;


@RunWith(PowerMockRunner.class)
@PrepareForTest({ReadFileBolt.class, ReadDatasetBolt.class, IcBolt.class, WriteRecordBolt.class, EndBolt.class, NotificationBolt.class})
@PowerMockIgnore({"javax.management.*", "javax.security.*"})
public class ICTopologyDataSetTest extends ICTestMocksHelper implements TestConstantsHelper {

    private final String datasetStream = "DATASET_STREAM";
    private final String fileStream = "FILE_STREAM";
    Map<String, String> routingRules;


    public ICTopologyDataSetTest() {

    }


    @Before
    public void setUp() throws Exception {
        mockZookeeperKS();
        mockRecordSC();
        mockFileSC();
        mockImageCS();
        mockDPSDAO();
        mockDatSetClient();
        routingRules = new HashMap<>();
        routingRules.put(PluginParameterKeys.FILE_URLS, datasetStream);
        routingRules.put(PluginParameterKeys.DATASET_URLS, fileStream);
    }


      @Test
    public void testTopologyWithDataSetsAsDataEntry() throws MCSException, MimeTypeException, IOException, ICSException, URISyntaxException {
        //given
        configureMocks();
        final String input = "{\"inputData\":" +
                "{\"DATASET_URLS\":" +
                "[\"" + SOURCE_DATASET_URL + "\",\"" + SOURCE_DATASET_URL2 + "\"]}," +
                "\"parameters\":" +
                "{\"MIME_TYPE\":\"image/tiff\"," +
                "\"OUTPUT_MIME_TYPE\":\"image/jp2\"," +
                "\"AUTHORIZATION_HEADER\":\"AUTHORIZATION_HEADER\"," +
                "\"TASK_SUBMITTER_NAME\":\"user\"}," +
                "\"taskId\":1," +
                "\"taskName\":\"taskName\"}";
        MkClusterParam mkClusterParam = prepareMKClusterParm();
        Testing.withSimulatedTimeLocalCluster(mkClusterParam, new TestJob() {
            @Override
            public void run(ILocalCluster cluster) throws JSONException {
                // build the test topology
                StormTopology topology = buildTopology();
                // prepare the mock data
                MockedSources mockedSources = new MockedSources();
                mockedSources.addMockData(SPOUT, new Values(input));

                CompleteTopologyParam completeTopologyParam = prepareCompleteTopologyParam(mockedSources);
                List<String> expectedTuples = Arrays.asList("[\n" +
                        "  [\n" +
                        "    1,\n" +
                        "    \"taskName\",\n" +
                        "    \"http://localhost:8080/mcs/records/sourceCloudId/representations/sourceRepresentationName/versions/sourceVersion/files/sourceFileName\",\n" +
                        "    [\n" +
                        "      116,\n" +
                        "      101,\n" +
                        "      115,\n" +
                        "      116,\n" +
                        "      67,\n" +
                        "      111,\n" +
                        "      110,\n" +
                        "      116,\n" +
                        "      101,\n" +
                        "      110,\n" +
                        "      116\n" +
                        "    ],\n" +
                        "    {\n" +
                        "      \"MIME_TYPE\": \"image/tiff\",\n" +
                        "      \"OUTPUT_MIME_TYPE\": \"image/jp2\",\n" +
                        "      \"AUTHORIZATION_HEADER\": \"AUTHORIZATION_HEADER\",\n" +
                        "      \"TASK_SUBMITTER_NAME\": \"user\",\n" +
                        "      \"DPS_TASK_INPUT_DATA\": \"{\\\"DATASET_URLS\\\":[\\\"http://localhost:8080/mcs/data-providers/testDataProvider/data-sets/dataSet\\\",\\\"http://localhost:8080/mcs/data-providers/testDataProvider/data-sets/dataSet2\\\"]}\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "]",
                        " [ [\n" +
                        "    1,\n" +
                        "    \"taskName\",\n" +
                        "    \"http://localhost:8080/mcs/records/sourceCloudId/representations/sourceRepresentationName/versions/sourceVersion2/files/sourceFileName\",\n" +
                        "    [\n" +
                        "      116,\n" +
                        "      101,\n" +
                        "      115,\n" +
                        "      116,\n" +
                        "      67,\n" +
                        "      111,\n" +
                        "      110,\n" +
                        "      116,\n" +
                        "      101,\n" +
                        "      110,\n" +
                        "      116\n" +
                        "    ],\n" +
                        "    {\n" +
                        "      \"MIME_TYPE\": \"image/tiff\",\n" +
                        "      \"OUTPUT_MIME_TYPE\": \"image/jp2\",\n" +
                        "      \"AUTHORIZATION_HEADER\": \"AUTHORIZATION_HEADER\",\n" +
                        "      \"TASK_SUBMITTER_NAME\": \"user\",\n" +
                        "      \"DPS_TASK_INPUT_DATA\": \"{\\\"DATASET_URLS\\\":[\\\"http://localhost:8080/mcs/data-providers/testDataProvider/data-sets/dataSet\\\",\\\"http://localhost:8080/mcs/data-providers/testDataProvider/data-sets/dataSet2\\\"]}\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "]");
                assertResultedTuple(cluster, topology, completeTopologyParam, expectedTuples);


            }
        });
    }


    private void printDefaultStreamTuples(Map result) {
        for (String boltResult : PRINT_ORDER) {
            prettyPrintJSON(Testing.readTuples(result, boltResult), boltResult);
        }
    }


    private void configureMocks() throws MCSException, MimeTypeException, IOException, ICSException, URISyntaxException {
        when(fileServiceClient.useAuthorizationHeader(anyString())).thenReturn(fileServiceClient);

        when(recordServiceClient.useAuthorizationHeader(anyString())).thenReturn(recordServiceClient);
        when(dataSetClient.useAuthorizationHeader(anyString())).thenReturn(dataSetClient);


        String fileUrl = "http://localhost:8080/mcs/records/sourceCloudId/representations/sourceRepresentationName/versions/sourceVersion/files/sourceFileName";
        when(fileServiceClient.getFile(fileUrl)).thenReturn(new ByteArrayInputStream("testContent".getBytes()));
        when(fileServiceClient.getFile("http://localhost:8080/mcs/records/sourceCloudId/representations/sourceRepresentationName/versions/sourceVersion2/files/sourceFileName")).thenReturn(new ByteArrayInputStream("testContent".getBytes()));


        List<File> files = new ArrayList<>();
        files.add(new File("sourceFileName","text/plain", "md5","1",5,new URI(fileUrl)));
        Representation representation = new Representation(SOURCE + CLOUD_ID, SOURCE + REPRESENTATION_NAME, SOURCE + VERSION, new URI(SOURCE_VERSION_URL), new URI(SOURCE_VERSION_URL), DATA_PROVIDER,files,false,new Date());
        List<Representation> representationList = new ArrayList<>();
        representationList.add(representation);


        List<File> files2 = new ArrayList<>();
        files2.add(new File("sourceFileName","text/plain", "md5","1",5,new URI(fileUrl)));
        Representation representation2 = new Representation(SOURCE + CLOUD_ID, SOURCE + REPRESENTATION_NAME, SOURCE + VERSION + 2, new URI(SOURCE_VERSION_URL2), new URI(SOURCE_VERSION_URL2), DATA_PROVIDER,files2,false,new Date());
        List<Representation> representationList2 = new ArrayList<>();
        representationList2.add(representation2);

        when(dataSetClient.getDataSetRepresentations("testDataProvider", "dataSet")).thenReturn(representationList);
        when(dataSetClient.getDataSetRepresentations("testDataProvider", "dataSet2")).thenReturn(representationList2);

        when(fileServiceClient.getFileUri(SOURCE + CLOUD_ID, SOURCE + REPRESENTATION_NAME, SOURCE + VERSION, SOURCE + FILE)).thenReturn(new URI(SOURCE_VERSION_URL));
        when(fileServiceClient.getFileUri(SOURCE + CLOUD_ID, SOURCE + REPRESENTATION_NAME, SOURCE + VERSION + 2, SOURCE + FILE)).thenReturn(new URI(SOURCE_VERSION_URL2));

        doNothing().when(imageConverterService).convertFile(any(StormTaskTuple.class));

        when(recordServiceClient.getRepresentation(SOURCE + CLOUD_ID, SOURCE + REPRESENTATION_NAME, SOURCE + VERSION)).thenReturn(representation);
        when(recordServiceClient.getRepresentation(SOURCE + CLOUD_ID, SOURCE + REPRESENTATION_NAME, SOURCE + VERSION + 2)).thenReturn(representation2);



    }

    private StormTopology buildTopology() {
        // build the test topology
        ReadFileBolt retrieveFileBolt = new ReadFileBolt("");
        ReadDatasetBolt readDatasetBolt = new ReadDatasetBolt("");
        NotificationBolt notificationBolt = new NotificationBolt("", 1, "", "", "");

        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout(SPOUT, new TestSpout(), 1);
        builder.setBolt(PARSE_TASK_BOLT, new ParseTaskBolt(routingRules, null)).shuffleGrouping(SPOUT);
        builder.setBolt(RETRIEVE_FILE_BOLT, retrieveFileBolt).shuffleGrouping(PARSE_TASK_BOLT, fileStream);
        builder.setBolt(RETRIEVE_DATASET_BOLT, readDatasetBolt).shuffleGrouping(PARSE_TASK_BOLT, datasetStream);
        builder.setBolt(IC_BOLT, new IcBolt()).shuffleGrouping(RETRIEVE_FILE_BOLT).shuffleGrouping(RETRIEVE_DATASET_BOLT);
        builder.setBolt(NOTIFICATION_BOLT, notificationBolt)
                .fieldsGrouping(PARSE_TASK_BOLT, AbstractDpsBolt.NOTIFICATION_STREAM_NAME, new Fields(NotificationTuple.taskIdFieldName))
                .fieldsGrouping(RETRIEVE_FILE_BOLT, AbstractDpsBolt.NOTIFICATION_STREAM_NAME, new Fields(NotificationTuple.taskIdFieldName))
                .fieldsGrouping(RETRIEVE_DATASET_BOLT, AbstractDpsBolt.NOTIFICATION_STREAM_NAME, new Fields(NotificationTuple.taskIdFieldName))
                .fieldsGrouping(IC_BOLT, AbstractDpsBolt.NOTIFICATION_STREAM_NAME, new Fields(NotificationTuple.taskIdFieldName));


        StormTopology topology = builder.createTopology();
        return topology;
    }

    private CompleteTopologyParam prepareCompleteTopologyParam(MockedSources mockedSources) {
        // prepare the config
        Config conf = new Config();
        conf.setNumWorkers(NUM_WORKERS);
        CompleteTopologyParam completeTopologyParam = new CompleteTopologyParam();
        completeTopologyParam.setMockedSources(mockedSources);
        completeTopologyParam.setStormConf(conf);
        return completeTopologyParam;

    }

    private void assertResultedTuple(ILocalCluster cluster, StormTopology topology, CompleteTopologyParam completeTopologyParam, List<String> expectedTuples) throws JSONException {
        Map result = Testing.completeTopology(cluster, topology,
                completeTopologyParam);
        //then
        printDefaultStreamTuples(result);

        List actualTuples = Testing.readTuples(result, IC_BOLT);
        for (int i = 0; i < expectedTuples.size(); i++) {
            String actual = parse(selectSingle(actualTuples, i));
            String expected = expectedTuples.get(i);
            assertEquals(expected, actual, false);
        }
    }

    private List selectSingle(List actualTuples, int index) {
        return Arrays.asList(actualTuples.get(index));
    }
}