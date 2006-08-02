//
// $Id$

package com.threerings.msoy.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;

import com.samskivert.util.Config;
import com.samskivert.util.StringUtil;

import com.threerings.presents.client.Client;

import static com.threerings.msoy.Log.log;

/**
 * Provides access to installation specific configuration. Properties that
 * are specific to a particular Bang! server installation are accessed via
 * this class.
 */
public class ServerConfig
{
    /** The name assigned to this server installation. */
    public static String serverName;

    /** The root directory of the server installation. */
    public static File serverRoot;

    /** The ports on which we are listening for client connections. */
    public static int[] serverPorts;

    /** The directory into which uploaded media is stored. */
    public static File mediaDir;

    /** Provides access to our config properties. <em>Do not</em> modify
     * these properties! */
    public static Config config;

    /**
     * Returns the JDBC configuration.
     */
    public static Properties getJDBCConfig ()
    {
        return config.getSubProperties("db");
    }

    /**
     * Returns the port on which the server should listen for HTTP connections.
     * The default is 8080 to avoid conflict with local web server
     * installations on development servers but the expectation is that in
     * production the server will listen directly on port 80.
     */
    public static int getHttpPort ()
    {
        return config.getValue("http_port", 8080);
    }

    /**
     * Configures the install config with the path to our installation
     * properties file. This method is called automatically.
     */
    protected static void init (String propPath)
    {
        Properties props = new Properties();
        try {
            if (propPath != null) {
                propPath = propPath + File.separator + "server.properties";
                props.load(new FileInputStream(propPath));
            } else {
                propPath = "server.properties";
                InputStream in = ServerConfig.class.getClassLoader().
                    getResourceAsStream(propPath);
                if (in == null) {
                    log.warning("Failed to locate server.properties " +
                                "on the classpath.");
                } else {
                    props.load(in);
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to load install properties " +
                    "[path=" + propPath + "].", e);
        }
        config = new Config("server", props);

        // fill in our standard properties
        serverName = config.getValue("server_name", "msoy");
        serverRoot = new File(config.getValue("server_root", "/tmp"));
        mediaDir = new File(config.getValue("media_dir", "/tmp"));
        serverPorts = config.getValue(
            "server_ports", Client.DEFAULT_SERVER_PORTS);
    }

    static {
        init(System.getProperty("msoy.home"));
    }
}
