package eu.europeana.cloud.dps.topologies.media;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.topology.IRichSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;
import org.slf4j.LoggerFactory;
import eu.europeana.cloud.dps.topologies.media.DataSetReaderSpout.Mode;
import eu.europeana.cloud.dps.topologies.media.support.DummySpout;
import eu.europeana.cloud.dps.topologies.media.support.FileTupleData;
import eu.europeana.cloud.dps.topologies.media.support.StatsInitTupleData;
import eu.europeana.cloud.dps.topologies.media.support.StatsTupleData;
import eu.europeana.cloud.dps.topologies.media.support.Util;
import eu.europeana.cloud.service.dps.storm.topologies.properties.TopologyPropertyKeys;

public class LinkCheckTopology {

    public static void main(String[] args) {
        try {
            Config conf = Util.loadConfig();

            final boolean isTest = args.length > 0;

            String topologyName =
                    (String) conf.computeIfAbsent(TopologyPropertyKeys.TOPOLOGY_NAME, k -> "linkcheck_topology");
            String source = "source";
            String linkCheckBolt = "linkCheckBolt";
            String statsBolt = "statsBolt";

            TopologyBuilder builder = new TopologyBuilder();
            IRichSpout baseSpout = isTest ? new DummySpout() : new KafkaSpout(Util.getKafkaSpoutConfig(conf));
            builder.setSpout(source, new DataSetReaderSpout(baseSpout, Mode.LINK_CHECKING), 1);

            builder.setBolt(linkCheckBolt, new LinkCheckBolt(), (Number) conf.get(Config.TOPOLOGY_WORKERS))
                    .fieldsGrouping(source, new Fields(DataSetReaderSpout.SOURCE_FIELD));

            builder.setBolt(statsBolt, new StatsBolt(), 1)
                    .globalGrouping(source, StatsInitTupleData.STREAM_ID)
                    .globalGrouping(source, StatsTupleData.STREAM_ID)
                    .globalGrouping(source, FileTupleData.STREAM_ID)
                    .globalGrouping(linkCheckBolt, StatsTupleData.STREAM_ID);

            if (isTest) {
                LocalCluster cluster = new LocalCluster();
                cluster.submitTopology(topologyName, conf, builder.createTopology());
                Utils.sleep(600000);
                cluster.killTopology(topologyName);
                cluster.shutdown();
            } else {
                StormSubmitter.submitTopology(topologyName, conf, builder.createTopology());
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(LinkCheckTopology.class).error("unexpected error", e);
        }
    }
}
