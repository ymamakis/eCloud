package eu.europeana.cloud.service.dps.services.postprocessors;

import eu.europeana.cloud.common.model.dps.TaskByTaskState;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

public class PostProcessorFactory {
    private final Map<String, TaskPostProcessor> services;

    @Inject
    public PostProcessorFactory(Map<String, TaskPostProcessor> services) {
        this.services = services;
    }

    public Optional<TaskPostProcessor> getPostProcessor(TaskByTaskState taskByTaskState) {
        return Optional.ofNullable(services.get(taskByTaskState.getTopologyName()));
    }
}
