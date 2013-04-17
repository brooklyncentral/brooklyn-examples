package brooklyn.extras.whirr;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.extras.whirr.core.WhirrCluster;
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WhirrHadoopExample extends ApplicationBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    public static final String DEFAULT_LOCATION = "aws-ec2:eu-west-1";

    @Override
    protected void doBuild() {
        WhirrCluster cluster = createChild(BasicEntitySpec.newInstance(WhirrHadoopCluster.class)
                .displayName("brooklyn-hadoop-example")
                .configure("size", 2)
                .configure("memory", 2048));
    }

    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(new WhirrHadoopExample())
                .webconsolePort(port)
                .location(location)
                .start();
         
        StartableApplication app = (StartableApplication) launcher.getApplications().get(0);
        Entities.dumpInfo(app);
        
        LOG.info("Press return to shut down the cluster");
        System.in.read(); //wait for the user to type a key
        app.stop();
    }
}
