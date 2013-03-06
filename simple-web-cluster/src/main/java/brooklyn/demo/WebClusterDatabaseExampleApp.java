package brooklyn.demo;

import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.java.JavaEntityMethods;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Demonstrates how to define a new Application Entity class (reusable and extensible),
 * as opposed to just using the builder as in {@link WebClusterDatabaseExample}.
 * With an app, when we define public static sensors and runtime config _on the app class_ 
 * (not the builder) they can be discovered at runtime.
 * <p>
 * This variant also increases minimum size to 2.  
 * Note the policy min size must have the same value,
 * otherwise it fights with cluster set up trying to reduce the cluster size!
 **/
public class WebClusterDatabaseExampleApp extends AbstractApplication implements StartableApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleApp.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static final String DEFAULT_WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    @CatalogConfig(label="WAR (URL)")
    public static final ConfigKey<String> WAR_PATH = new BasicConfigKey<String>(String.class,
        "app.war", "URL to the application archive which should be deployed", 
        DEFAULT_WAR_PATH);

    // TODO to expose in catalog we need to let the keystore url be specified (not hard)
    // and also confirm that this works for nginx (might be a bit fiddly);
    // booleans in the gui are working (With checkbox)
//    @CatalogConfig(label="HTTPS")
    public static final ConfigKey<Boolean> USE_HTTPS = new BasicConfigKey<Boolean>(Boolean.class,
            "app.https", "Whether the application should use HTTPS only or just HTTP only (default)", false);
    
    public static final String DEFAULT_DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    @CatalogConfig(label="DB Setup SQL (URL)")
    public static final ConfigKey<String> DB_SETUP_SQL_URL = new BasicConfigKey<String>(String.class,
        "app.db_sql", "URL to the SQL script to set up the database", 
        DEFAULT_DB_SETUP_SQL_URL);
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    BasicAttributeSensor<Integer> APPSERVERS_COUNT = new BasicAttributeSensor<Integer>(Integer.class, 
            "appservers.count", "Number of app servers deployed");
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW = 
            WebAppServiceConstants.REQUESTS_PER_SECOND_IN_WINDOW;
    public static final AttributeSensor<String> ROOT_URL = WebAppServiceConstants.ROOT_URL;

    @Override
    public void postConstruct() {
        super.postConstruct();

        MySqlNode mysql = (MySqlNode) addChild(
                getEntityManager().createEntity(
                        BasicEntitySpec.newInstance(MySqlNode.class)
                        .configure(MySqlNode.CREATION_SCRIPT_URL, getConfig(DB_SETUP_SQL_URL))) );

        ControlledDynamicWebAppCluster web = (ControlledDynamicWebAppCluster) addChild(
                getEntityManager().createEntity(
                        BasicEntitySpec.newInstance(ControlledDynamicWebAppCluster.class)
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        .configure(JavaWebAppService.ROOT_WAR, getConfig(WAR_PATH))
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.MYSQL_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                        .configure(DynamicCluster.INITIAL_SIZE, 2)
                        .configure(WebAppService.ENABLED_PROTOCOLS, Arrays.asList(getConfig(USE_HTTPS) ? "https" : "http")) ));

        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(2, 5).
                build());

        addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(web,  
                WebAppServiceConstants.ROOT_URL,
                DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW));
        addEnricher(new SensorTransformingEnricher<Integer,Integer>(web, 
                DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT, Functions.<Integer>identity()));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        StartableApplication app = ApplicationBuilder.builder(WebClusterDatabaseExampleApp.class)
                .displayName("Brooklyn WebApp Cluster with Database example")
                .manage(server.getManagementContext());

        app.start(ImmutableList.of(loc));

        Entities.dumpInfo(app);
    }
    
}
