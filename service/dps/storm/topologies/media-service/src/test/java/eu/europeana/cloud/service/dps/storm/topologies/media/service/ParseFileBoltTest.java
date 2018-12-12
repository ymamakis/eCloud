package eu.europeana.cloud.service.dps.storm.topologies.media.service;


import eu.europeana.cloud.common.model.dps.States;
import eu.europeana.cloud.mcs.driver.FileServiceClient;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.storm.StormTaskTuple;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by Tarek on 12/6/2018.
 */
@RunWith(MockitoJUnitRunner.class)
public class ParseFileBoltTest {

    private static final String AUTHORIZATION_HEADER = "AUTHORIZATION_HEADER";
    private static final String AUTHORIZATION = "Authorization";
    public static final String RESOURCE_LINK_KEY = "RESOURCE_LINK";
    public static final String DPS_TASK_INPUT_DATA = "DPS_TASK_INPUT_DATA";
    public static final String FILE_URL = "FILE_URL";
    public static final String NOTIFICATION_STREAM_NAME = "NotificationStream";
    public static final String RESOURCE_LINKS_COUNT = "RESOURCE_LINKS_COUNT";

    @Mock(name = "outputCollector")
    private OutputCollector outputCollector;

    @Mock(name = "fileClient")
    protected FileServiceClient fileClient;

    @Captor
    ArgumentCaptor<Values> captor = ArgumentCaptor.forClass(Values.class);


    @InjectMocks
    static ParseFileBolt parseFileBolt = new ParseFileBolt("localhost/mcs");

    private StormTaskTuple stormTaskTuple;
    private static List<String> expectedParametersKeysList;

    @BeforeClass
    public static void init() {

        parseFileBolt.prepare();
        expectedParametersKeysList = Arrays.asList(AUTHORIZATION_HEADER, RESOURCE_LINK_KEY, DPS_TASK_INPUT_DATA, RESOURCE_LINKS_COUNT);

    }

    @Before
    public void prepareTuple() {
        MockitoAnnotations.initMocks(this);
        stormTaskTuple = new StormTaskTuple();
        stormTaskTuple.setFileUrl(FILE_URL);
        stormTaskTuple.addParameter(PluginParameterKeys.DPS_TASK_INPUT_DATA, FILE_URL);
        stormTaskTuple.addParameter(AUTHORIZATION_HEADER, AUTHORIZATION);
    }

    @Test
    public void shouldParseFileAndEmitResources() throws Exception {
        try (InputStream stream = this.getClass().getResourceAsStream("/files/Item_35834473.xml")) {
            when(fileClient.getFile(eq(FILE_URL), eq(AUTHORIZATION), eq(AUTHORIZATION))).thenReturn(stream);
            parseFileBolt.execute(stormTaskTuple);
            verify(outputCollector, Mockito.times(5)).emit(captor.capture()); // 4 hasView, 1 edm:object

            List<Values> capturedValuesList = captor.getAllValues();
            assertEquals(5, capturedValuesList.size());
            for (Values values : capturedValuesList) {
                assertEquals(7, values.size());
                Map<String, String> val = (Map) values.get(4);
                assertNotNull(val);
                for (String parameterKey : val.keySet()) {
                    assertTrue(expectedParametersKeysList.contains(parameterKey));
                }
            }
        }
    }

    @Test
    public void shouldParseFileWithEmptyResourcesAndForwardOneTuple() throws Exception {
        try (InputStream stream = this.getClass().getResourceAsStream("/files/no-resources.xml")) {
            when(fileClient.getFile(eq(FILE_URL), eq(AUTHORIZATION), eq(AUTHORIZATION))).thenReturn(stream);
            parseFileBolt.execute(stormTaskTuple);
            verify(outputCollector, Mockito.times(1)).emit(captor.capture());
            Values values = captor.getValue();
            assertNotNull(values);
            System.out.println(values);
            Map<String, String> map = (Map) values.get(4);
            System.out.println(map);
            assertEquals(2, map.size());
            assertNull(map.get(RESOURCE_LINKS_COUNT));
            assertNull(map.get(RESOURCE_LINK_KEY));
        }

    }

    @Test
    public void shouldEmitErrorWhenDownloadFileFails() throws Exception {
        doThrow(IOException.class).when(fileClient).getFile(eq(FILE_URL), eq(AUTHORIZATION), eq(AUTHORIZATION));
        parseFileBolt.execute(stormTaskTuple);
        verify(outputCollector, Mockito.times(1)).emit(eq(NOTIFICATION_STREAM_NAME), captor.capture());
        Values values = captor.getValue();
        assertNotNull(values);
        Map<String, String> valueMap = (Map) values.get(2);
        assertNotNull(valueMap);
        assertEquals(4, valueMap.size());
        assertTrue(valueMap.get("additionalInfo").contains("Error while reading and parsing the EDM file"));
        assertEquals(States.ERROR.toString(), valueMap.get("state"));
        assertNull(valueMap.get(RESOURCE_LINKS_COUNT));
        verify(outputCollector, Mockito.times(0)).emit(anyList());
    }


    @Test
    public void shouldEmitErrorWhenGettingResourceLinksFails() throws Exception {
        try (InputStream stream = this.getClass().getResourceAsStream("/files/broken.xml")) {
            when(fileClient.getFile(eq(FILE_URL), eq(AUTHORIZATION), eq(AUTHORIZATION))).thenReturn(stream);
            parseFileBolt.execute(stormTaskTuple);
            verify(outputCollector, Mockito.times(1)).emit(eq(NOTIFICATION_STREAM_NAME), captor.capture());
            Values values = captor.getValue();
            assertNotNull(values);
            Map<String, String> valueMap = (Map) values.get(2);
            assertNotNull(valueMap);
            assertEquals(4, valueMap.size());
            assertTrue(valueMap.get("additionalInfo").contains("Error while reading and parsing the EDM file"));
            assertEquals(States.ERROR.toString(), valueMap.get("state"));
            assertNull(valueMap.get(RESOURCE_LINKS_COUNT));
            verify(outputCollector, Mockito.times(0)).emit(anyList());
        }

    }
}