package eu.europeana.cloud.service.dps.storm;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;

/**
 * Increases progress for the current Task by 1 for every tuple received.
 * 
 * @author manos
 *
 */
public class ProgressBolt extends AbstractDpsBolt {

	private OutputCollector collector;

	private static final Logger LOGGER = LoggerFactory.getLogger(ProgressBolt.class);

	private transient ZookeeperMultiCountMetric zMetric;
	private String zkAddress;
	
	public ProgressBolt(String zkAddress) {
		this.zkAddress = zkAddress;
	}

	@Override
	public void prepare(Map conf, TopologyContext context,
			OutputCollector collector) {

		this.collector = collector;
		initMetrics(context);
	}

	void initMetrics(TopologyContext context) {
		
		zMetric = new ZookeeperMultiCountMetric(zkAddress);
		context.registerMetric("zMetric=>", zMetric, 1);
	}

	@Override
	public void execute(StormTaskTuple t) {

		try {
			updateProgress(t);
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
		collector.emit(t.toStormTuple());
	}

	private void updateProgress(StormTaskTuple t) {
		
		zMetric.incr(t.getTaskId());
		LOGGER.info("ProgressIncreaseBolt: progress updated");
	}
}