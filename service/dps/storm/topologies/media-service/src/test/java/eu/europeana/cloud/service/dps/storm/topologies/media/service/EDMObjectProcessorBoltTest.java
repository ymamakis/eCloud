package eu.europeana.cloud.service.dps.storm.topologies.media.service;


import com.amazonaws.services.s3.model.ObjectMetadata;
import eu.europeana.cloud.mcs.driver.FileServiceClient;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.storm.StormTaskTuple;
import eu.europeana.cloud.service.dps.storm.utils.TaskStatusChecker;
import eu.europeana.metis.mediaprocessing.MediaExtractor;
import eu.europeana.metis.mediaprocessing.MediaProcessorFactory;
import eu.europeana.metis.mediaprocessing.RdfConverterFactory;
import eu.europeana.metis.mediaprocessing.RdfDeserializer;
import eu.europeana.metis.mediaprocessing.exception.MediaExtractionException;
import eu.europeana.metis.mediaprocessing.exception.MediaProcessorException;
import eu.europeana.metis.mediaprocessing.model.RdfResourceEntry;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class EDMObjectProcessorBoltTest {

    @Captor
    ArgumentCaptor<Values> captor = ArgumentCaptor.forClass(Values.class);
    @Mock(name = "outputCollector")
    private OutputCollector outputCollector;
    @Mock(name = "fileClient")
    private FileServiceClient fileClient;
    @Mock(name = "taskStatusChecker")
    private TaskStatusChecker taskStatusChecker;
    @Spy
    private transient MediaExtractor mediaExtractor;
    @Spy
    private transient RdfDeserializer rdfDeserializer;

    private final AmazonClient amazonClient = Mockito.mock(AmazonClient.class);

    @InjectMocks
    private final EDMObjectProcessorBolt edmObjectProcessorBolt = new EDMObjectProcessorBolt("MCS_URL", amazonClient);

    @Before
    public void init() throws MediaProcessorException {
        edmObjectProcessorBolt.prepare();
        mediaExtractor = new MediaProcessorFactory().createMediaExtractor();
        rdfDeserializer = new RdfConverterFactory().createRdfDeserializer();
        MockitoAnnotations.initMocks(this); // initialize all the @Mock objects
    }

    @Test
    public void shouldDoProperEmissionInCaseOfFileWithSingleResource() throws Exception {
        //given
        try (InputStream stream = this.getClass().getResourceAsStream("/files/fileWithSingleResource.xml")) {
            when(fileClient.getFile(anyString(), anyString(), anyString())).thenReturn(stream);
            StormTaskTuple tuple = new StormTaskTuple();
            tuple.addParameter(PluginParameterKeys.CLOUD_LOCAL_IDENTIFIER, "example");
            tuple.addParameter(PluginParameterKeys.AUTHORIZATION_HEADER, "sample_auth");
            //
            Tuple anchorTuple = mock(TupleImpl.class);

            //when
            edmObjectProcessorBolt.execute(anchorTuple, tuple);
            //then
            verify(outputCollector, times(1)).emit(eq(EDMObjectProcessorBolt.EDM_OBJECT_ENRICHMENT_STREAM_NAME), any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).emit(any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).ack(any(Tuple.class));
            Values valuesForEnrichmentBolt = captor.getValue();
            Values valuesForParseFileBolt = captor.getValue();
            //
            Map<String, String> parametersForEnrichmentBolt = (Map) valuesForEnrichmentBolt.get(4);
            Map<String, String> parametersForParseFileBolt = (Map) valuesForParseFileBolt.get(4);
            assertEquals("1", parametersForParseFileBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
            assertEquals("1", parametersForEnrichmentBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
        }
    }

    @Test
    public void shouldDoProperEmissionInCaseOfResourceProcessingExceptionForSingleResourceFile() throws Exception {
        //given
        try (InputStream stream = this.getClass().getResourceAsStream("/files/fileWithSingleResource.xml")) {
            when(fileClient.getFile(anyString(), anyString(), anyString())).thenReturn(stream);
            doThrow(MediaExtractionException.class).when(mediaExtractor).performMediaExtraction(any(RdfResourceEntry.class), anyBoolean());

            StormTaskTuple tuple = new StormTaskTuple();
            tuple.addParameter(PluginParameterKeys.CLOUD_LOCAL_IDENTIFIER, "example");
            tuple.addParameter(PluginParameterKeys.AUTHORIZATION_HEADER, "sample_auth");
            //
            Tuple anchorTuple = mock(TupleImpl.class);

            //when
            edmObjectProcessorBolt.execute(anchorTuple, tuple);
            //then
            verify(outputCollector, times(1)).emit(eq(EDMObjectProcessorBolt.EDM_OBJECT_ENRICHMENT_STREAM_NAME), any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).emit(any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).ack(any(Tuple.class));
            Values valuesForEnrichmentBolt = captor.getValue();
            Values valuesForParseFileBolt = captor.getValue();


            Map<String, String> parametersForEnrichmentBolt = (Map) valuesForEnrichmentBolt.get(4);
            Map<String, String> parametersForParseFileBolt = (Map) valuesForParseFileBolt.get(4);
            assertEquals("1", parametersForParseFileBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
            assertEquals("1", parametersForEnrichmentBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
        }
    }

    @Test
    public void shouldDoProperEmissionInCaseOfResourceProcessingExceptionForTwoResourcesFile() throws Exception {
        //given
        try (InputStream stream = this.getClass().getResourceAsStream("/files/fileWithTwoResources.xml")) {
            when(fileClient.getFile(anyString(), anyString(), anyString())).thenReturn(stream);
            doThrow(MediaExtractionException.class).when(mediaExtractor).performMediaExtraction(any(RdfResourceEntry.class), anyBoolean());

            StormTaskTuple tuple = new StormTaskTuple();
            tuple.addParameter(PluginParameterKeys.CLOUD_LOCAL_IDENTIFIER, "example");
            tuple.addParameter(PluginParameterKeys.AUTHORIZATION_HEADER, "sample_auth");
            //
            Tuple anchorTuple = mock(TupleImpl.class);

            //when
            edmObjectProcessorBolt.execute(anchorTuple, tuple);
            //then
            verify(outputCollector, times(1)).emit(eq(EDMObjectProcessorBolt.EDM_OBJECT_ENRICHMENT_STREAM_NAME), any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).emit(any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).ack(any(Tuple.class));
            Values valuesForEnrichmentBolt = captor.getValue();
            Values valuesForParseFileBolt = captor.getValue();

            Map<String, String> parametersForEnrichmentBolt = (Map) valuesForEnrichmentBolt.get(4);
            Map<String, String> parametersForParseFileBolt = (Map) valuesForParseFileBolt.get(4);
            assertEquals("2", parametersForParseFileBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
            assertEquals("2", parametersForEnrichmentBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
        }
    }

    @Test
    public void shouldDoProperEmissionInCaseOfFileContainingNoMainThumbnailResource() throws Exception {
        //given
        try (InputStream stream = this.getClass().getResourceAsStream("/files/fileWithTwoResources.xml")) {
            when(fileClient.getFile(anyString(), anyString(), anyString())).thenReturn(stream);

            doReturn(null).when(rdfDeserializer).getMainThumbnailResourceForMediaExtraction(any(byte[].class));

            StormTaskTuple tuple = new StormTaskTuple();
            tuple.addParameter(PluginParameterKeys.CLOUD_LOCAL_IDENTIFIER, "example");
            tuple.addParameter(PluginParameterKeys.AUTHORIZATION_HEADER, "sample_auth");
            //
            Tuple anchorTuple = mock(TupleImpl.class);

            //when
            edmObjectProcessorBolt.execute(anchorTuple, tuple);
            //then
            verify(outputCollector, times(0)).emit(eq(EDMObjectProcessorBolt.EDM_OBJECT_ENRICHMENT_STREAM_NAME), any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).emit(any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).ack(any(Tuple.class));
            Values valuesForParseFileBolt = captor.getValue();

            Map<String, String> parametersForParseFileBolt = (Map) valuesForParseFileBolt.get(4);
            assertEquals("1", parametersForParseFileBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
        }
    }
    @Test
    public void shouldDoProperEmissionInCaseOfFileExceptionWhileStoringTheThumbnail() throws Exception {
        //given
        try (InputStream stream = this.getClass().getResourceAsStream("/files/fileWithTwoResources.xml")) {
            when(fileClient.getFile(anyString(), anyString(), anyString())).thenReturn(stream);

            when(amazonClient.putObject(anyString(), any(InputStream.class), any(ObjectMetadata.class))).thenThrow(new RuntimeException());
            doThrow(new RuntimeException()).when(amazonClient).putObject(anyString(), anyString(), any(InputStream.class), nullable(ObjectMetadata.class));

            StormTaskTuple tuple = new StormTaskTuple();
            tuple.addParameter(PluginParameterKeys.CLOUD_LOCAL_IDENTIFIER, "example");
            tuple.addParameter(PluginParameterKeys.AUTHORIZATION_HEADER, "sample_auth");
            //
            Tuple anchorTuple = mock(TupleImpl.class);

            //when
            edmObjectProcessorBolt.execute(anchorTuple, tuple);
            //then
            verify(outputCollector, times(1)).emit(eq(EDMObjectProcessorBolt.EDM_OBJECT_ENRICHMENT_STREAM_NAME), any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).emit(any(Tuple.class), captor.capture());
            verify(outputCollector, times(1)).ack(any(Tuple.class));
            Values valuesForParseFileBolt = captor.getValue();

            Map<String, String> parametersForParseFileBolt = (Map) valuesForParseFileBolt.get(4);
            assertEquals("2", parametersForParseFileBolt.get(PluginParameterKeys.RESOURCE_LINKS_COUNT));
            assertEquals("media resource exception", parametersForParseFileBolt.get(PluginParameterKeys.UNIFIED_ERROR_MESSAGE));
            assertTrue(parametersForParseFileBolt.get(PluginParameterKeys.EXCEPTION_ERROR_MESSAGE).contains("Error while uploading"));
        }
    }
}