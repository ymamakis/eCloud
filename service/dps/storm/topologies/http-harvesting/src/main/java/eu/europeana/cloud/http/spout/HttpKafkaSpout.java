package eu.europeana.cloud.http.spout;

import com.rits.cloning.Cloner;
import eu.europeana.cloud.common.model.dps.States;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.cloud.http.common.CompressionFileExtension;
import eu.europeana.cloud.http.common.UnpackingServiceFactory;
import eu.europeana.cloud.http.exceptions.CompressionExtensionNotRecognizedException;
import eu.europeana.cloud.http.service.FileUnpackingService;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.InputDataType;
import eu.europeana.cloud.service.dps.OAIPMHHarvestingDetails;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.storm.NotificationTuple;
import eu.europeana.cloud.service.dps.storm.StormTaskTuple;
import eu.europeana.cloud.service.dps.storm.spouts.kafka.CustomKafkaSpout;
import eu.europeana.metis.transformation.service.EuropeanaGeneratedIdsMap;
import eu.europeana.metis.transformation.service.EuropeanaIdCreator;
import eu.europeana.metis.transformation.service.EuropeanaIdException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.spout.ISpoutOutputCollector;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static eu.europeana.cloud.service.dps.storm.AbstractDpsBolt.NOTIFICATION_STREAM_NAME;

/**
 * Created by Tarek on 4/27/2018.
 */
public class HttpKafkaSpout extends CustomKafkaSpout {

    private SpoutOutputCollector collector;
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpKafkaSpout.class);

    private static final int BATCH_MAX_SIZE = 1240 * 4;
    private static final String CLOUD_SEPARATOR = "_";
    private static final String MAC_TEMP_FOLDER = "__MACOSX";
    private static final String MAC_TEMP_FILE = ".DS_Store";

    TaskDownloader taskDownloader;

    HttpKafkaSpout(SpoutConfig spoutConf) {
        super(spoutConf);
        taskDownloader = new TaskDownloader();
    }

    public HttpKafkaSpout(SpoutConfig spoutConf, String hosts, int port, String keyspaceName,
                          String userName, String password) {
        super(spoutConf, hosts, port, keyspaceName, userName, password);

    }


    @Override
    public void open(Map conf, TopologyContext context,
                     SpoutOutputCollector collector) {
        taskDownloader = new TaskDownloader();
        this.collector = collector;
        super.open(conf, context, new CollectorWrapper(collector));
    }


    @Override
    public void nextTuple() {
        StormTaskTuple stormTaskTuple = null;
        try {
            super.nextTuple();
            stormTaskTuple = taskDownloader.getTupleWithFileURL();
            if (stormTaskTuple != null) {
                collector.emit(stormTaskTuple.toStormTuple());
            }
        } catch (Exception e) {
            LOGGER.error("Spout error: {}", e.getMessage());
            if (stormTaskTuple != null)
                cassandraTaskInfoDAO.dropTask(stormTaskTuple.getTaskId(), "The task was dropped because " + e.getMessage(), TaskState.DROPPED.toString());
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(StormTaskTuple.getFields());
        declarer.declareStream(NOTIFICATION_STREAM_NAME, NotificationTuple.getFields());
    }

    @Override
    public void deactivate() {
        LOGGER.info("Deactivate method was executed");
        deactivateWaitingTasks();
        deactivateCurrentTask();
        LOGGER.info("Deactivate method was finished");
    }

    private void deactivateWaitingTasks() {
        DpsTask dpsTask;
        while ((dpsTask = taskDownloader.taskQueue.poll()) != null)
            cassandraTaskInfoDAO.dropTask(dpsTask.getTaskId(), "The task was dropped because of redeployment", TaskState.DROPPED.toString());
    }

    private void deactivateCurrentTask() {
        DpsTask currentDpsTask = taskDownloader.getCurrentDpsTask();
        if (currentDpsTask != null) {
            cassandraTaskInfoDAO.dropTask(currentDpsTask.getTaskId(), "The task was dropped because of redeployment", TaskState.DROPPED.toString());
        }
    }


    class TaskDownloader extends Thread {
        private static final int MAX_SIZE = 100;
        ArrayBlockingQueue<DpsTask> taskQueue = new ArrayBlockingQueue<>(MAX_SIZE);
        ArrayBlockingQueue<StormTaskTuple> tuplesWithFileUrls = new ArrayBlockingQueue<>(MAX_SIZE);
        private DpsTask currentDpsTask;


        public TaskDownloader() {
            start();
        }

        public StormTaskTuple getTupleWithFileURL() {
            return tuplesWithFileUrls.poll();
        }

        public void addNewTask(DpsTask dpsTask) {
            try {
                taskQueue.put(dpsTask);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void run() {
            StormTaskTuple stormTaskTuple = null;
            while (true) {
                try {
                    currentDpsTask = taskQueue.take();
                    startProgress(currentDpsTask.getTaskId());
                    stormTaskTuple = new StormTaskTuple(
                            currentDpsTask.getTaskId(),
                            currentDpsTask.getTaskName(),
                            currentDpsTask.getDataEntry(InputDataType.REPOSITORY_URLS).get(0), null, currentDpsTask.getParameters(), currentDpsTask.getOutputRevision(), new OAIPMHHarvestingDetails());
                    execute(stormTaskTuple);
                } catch (Exception e) {
                    LOGGER.error("StaticDpsTaskSpout error: {}", e.getMessage());
                    if (stormTaskTuple != null)
                        cassandraTaskInfoDAO.dropTask(stormTaskTuple.getTaskId(), "The task was dropped because " + e.getMessage(), TaskState.DROPPED.toString());
                }
            }
        }

        private void startProgress(long taskId) {
            LOGGER.info("Start progressing for Task with id {}", currentDpsTask.getTaskId());
            cassandraTaskInfoDAO.updateTask(taskId, "", String.valueOf(TaskState.CURRENTLY_PROCESSING), new Date());

        }

        private DpsTask getCurrentDpsTask() {
            return currentDpsTask;
        }


        private void emitErrorNotification(long taskId, String resource, String message, String additionalInformations) {
            NotificationTuple nt = NotificationTuple.prepareNotification(taskId,
                    resource, States.ERROR, message, additionalInformations);
            collector.emit(NOTIFICATION_STREAM_NAME, nt.toStormTuple());
        }

        void execute(StormTaskTuple stormTaskTuple) throws CompressionExtensionNotRecognizedException, IOException, InterruptedException {
            File file = null;
            int expectedSize = 0;
            try {
                final boolean useDefaultIdentifiers = useDefaultIdentifier(stormTaskTuple);
                String metisDatasetId = null;
                if (!useDefaultIdentifiers) {
                    metisDatasetId = stormTaskTuple.getParameter(PluginParameterKeys.METIS_DATASET_ID);
                    if (StringUtils.isEmpty(metisDatasetId)) {
                        cassandraTaskInfoDAO.dropTask(stormTaskTuple.getTaskId(), "The task was dropped because METIS_DATASET_ID not provided", TaskState.DROPPED.toString());
                        return;
                    }
                }
                String httpURL = stormTaskTuple.getFileUrl();
                file = downloadFile(httpURL);
                String compressingExtension = FilenameUtils.getExtension(file.getName());
                FileUnpackingService fileUnpackingService = UnpackingServiceFactory.createUnpackingService(compressingExtension);
                fileUnpackingService.unpackFile(file.getAbsolutePath(), file.getParent() + File.separator);
                Path start = Paths.get(new File(file.getParent()).toURI());
                expectedSize = iterateOverFiles(start, stormTaskTuple, useDefaultIdentifiers, metisDatasetId);
                cassandraTaskInfoDAO.setUpdateExpectedSize(stormTaskTuple.getTaskId(), expectedSize);
            } finally {
                removeTempFolder(file);
                if (expectedSize == 0)
                    cassandraTaskInfoDAO.dropTask(stormTaskTuple.getTaskId(), "The task was dropped because it is empty", TaskState.DROPPED.toString());

            }
        }

        private File downloadFile(String httpURL) throws IOException {
            URL url = new URL(httpURL);
            URLConnection conn = url.openConnection();
            InputStream inputStream = conn.getInputStream();
            OutputStream outputStream = null;
            try {
                String tempFileName = UUID.randomUUID().toString();
                String folderPath = Files.createTempDirectory(tempFileName) + File.separator;
                File file = new File(folderPath + FilenameUtils.getName(httpURL));
                outputStream = new FileOutputStream(file.toPath().toString());
                byte[] buffer = new byte[BATCH_MAX_SIZE];
                IOUtils.copyLarge(inputStream, outputStream, buffer);
                return file;
            } finally {
                if (outputStream != null)
                    outputStream.close();
                if (inputStream != null)
                    inputStream.close();
            }
        }


        private int iterateOverFiles(final Path start, final StormTaskTuple stormTaskTuple, final boolean useDefaultIdentifiers, final String metisDatasetId) throws IOException, InterruptedException {
            final AtomicInteger expectedSize = new AtomicInteger(0);
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (taskStatusChecker.hasKillFlag(stormTaskTuple.getTaskId()))
                        return FileVisitResult.TERMINATE;
                    String fileName = getFileNameFromPath(file);
                    if (fileName.equals(MAC_TEMP_FILE))
                        return FileVisitResult.CONTINUE;
                    String extension = FilenameUtils.getExtension(file.toString());
                    if (!CompressionFileExtension.contains(extension)) {
                        String mimeType = Files.probeContentType(file);
                        String filePath = file.toString();
                        String readableFileName = filePath.substring(start.toString().length() + 1).replaceAll("\\\\", "/");
                        try {
                            prepareTuple(stormTaskTuple, filePath, readableFileName, mimeType, useDefaultIdentifiers, metisDatasetId);
                            expectedSize.set(expectedSize.incrementAndGet());
                        } catch (IOException | EuropeanaIdException | InterruptedException e) {
                            emitErrorNotification(stormTaskTuple.getTaskId(), readableFileName, "Error while reading the file because of " + e.getMessage(), "");
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = getFileNameFromPath(dir);
                    if (dirName.equals(MAC_TEMP_FOLDER))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
            });

            return expectedSize.get();
        }


        private String getFileNameFromPath(Path path) {
            if (path != null)
                return path.getFileName().toString();
            throw new IllegalArgumentException("Path parameter should never be null");
        }

        private void prepareTuple(StormTaskTuple stormTaskTuple, String filePath, String readableFilePath, String mimeType, boolean useDefaultIdentifiers, String datasetId) throws IOException, InterruptedException, EuropeanaIdException {
            FileInputStream fileInputStream = null;
            try {
                StormTaskTuple tuple = new Cloner().deepClone(stormTaskTuple);
                File file = new File(filePath);
                fileInputStream = new FileInputStream(file);
                tuple.setFileData(fileInputStream);
                tuple.addParameter(PluginParameterKeys.OUTPUT_MIME_TYPE, mimeType);

                String localId;
                if (useDefaultIdentifiers)
                    localId = formulateLocalId(readableFilePath);
                else {
                    EuropeanaGeneratedIdsMap europeanaIdentifier = getEuropeanaIdentifier(tuple, datasetId);
                    localId = europeanaIdentifier.getEuropeanaGeneratedId();
                    tuple.addParameter(PluginParameterKeys.ADDITIONAL_LOCAL_IDENTIFIER, europeanaIdentifier.getSourceProvidedChoAbout());
                }
                tuple.addParameter(PluginParameterKeys.CLOUD_LOCAL_IDENTIFIER, localId);
                tuple.setFileUrl(readableFilePath);
                tuplesWithFileUrls.put(tuple);
            } finally {
                if (fileInputStream != null)
                    fileInputStream.close();
            }
        }

        private boolean useDefaultIdentifier(StormTaskTuple stormTaskTuple) {
            boolean useDefaultIdentifiers = false;
            if ("true".equals(stormTaskTuple.getParameter(PluginParameterKeys.USE_DEFAULT_IDENTIFIERS))) {
                useDefaultIdentifiers = true;
            }
            return useDefaultIdentifiers;
        }

        private EuropeanaGeneratedIdsMap getEuropeanaIdentifier(StormTaskTuple stormTaskTuple, String datasetId) throws EuropeanaIdException {
            String document = new String(stormTaskTuple.getFileData());
            EuropeanaIdCreator europeanIdCreator = new EuropeanaIdCreator();
            return europeanIdCreator.constructEuropeanaId(document, datasetId);
        }

        private String formulateLocalId(String readableFilePath) {
            return new StringBuilder(readableFilePath).append(CLOUD_SEPARATOR).append(UUID.randomUUID().toString()).toString();
        }

        private void removeTempFolder(File file) {
            if (file != null)
                try {
                    FileUtils.deleteDirectory(new File(file.getParent()));
                } catch (IOException e) {
                    LOGGER.error("ERROR while removing the temp Folder: {}", e.getMessage());
                }
        }


    }

    private class CollectorWrapper extends SpoutOutputCollector {

        CollectorWrapper(ISpoutOutputCollector delegate) {
            super(delegate);
        }

        @Override
        public List<Integer> emit(String streamId, List<Object> tuple, Object messageId) {
            try {
                DpsTask dpsTask = new ObjectMapper().readValue((String) tuple.get(0), DpsTask.class);
                if (dpsTask != null) {
                    taskDownloader.addNewTask(dpsTask);
                }
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }

            return Collections.emptyList();
        }
    }
}