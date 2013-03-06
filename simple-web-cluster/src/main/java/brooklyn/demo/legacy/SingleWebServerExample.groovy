package brooklyn.demo.legacy;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.jboss.JBoss7ServerImpl
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

/** This example starts one web app on 8080, waits for a keypress, then stops it. */
public class SingleWebServerExample extends AbstractApplication {

    public static final Logger LOG = LoggerFactory.getLogger(SingleWebServerExample)

    public static final List<LocalhostMachineProvisioningLocation> DEFAULT_LOCATIONS = [ CommandLineLocations.newLocalhostLocation() ]
    private static final String WAR_PATH = "classpath://hello-world-webapp.war"

    JBoss7Server web = new JBoss7ServerImpl(this, war: WAR_PATH, httpPort: 8080)

    public static void main(String[] args) {
        SingleWebServerExample app = new SingleWebServerExample();
        LOG.info("created, now starting...");
        app.start(DEFAULT_LOCATIONS);
        LOG.info("started, visit "+app.web.getAttribute(JBoss7Server.ROOT_URL)+
            "\n---- then press enter to continue ----");
        System.in.read();
        LOG.info("now ending...");
        app.stop();
        LOG.info("ended.");
    }
    
}
