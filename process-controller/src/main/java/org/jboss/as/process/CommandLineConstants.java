/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.process;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class CommandLineConstants {

    /** The HostController address */
    public static final String OLD_INTERPROCESS_HC_ADDRESS = "-interprocess-hc-address";
    public static final String INTERPROCESS_HC_ADDRESS = "--interprocess-hc-address";

    /** The HostController port */
    public static final String OLD_INTERPROCESS_HC_PORT = "-interprocess-hc-port";
    public static final String INTERPROCESS_HC_PORT = "--interprocess-hc-port";

    /** Get the version of the server */
    public static final String OLD_VERSION = "-version";
    public static final String VERSION = "--version";
    public static final String SHORT_VERSION = "-v";
    public static final String OLD_SHORT_VERSION = "-V";

    /** Debug flag */
    public static final String DEBUG = "--debug";

    /** Configure the file to be used to read properties */
    public static final String OLD_PROPERTIES = "-properties";
    public static final String PROPERTIES = "--properties";
    public static final String SHORT_PROPERTIES = "-P";

    /** Configure a default jvm */
    public static final String OLD_DEFAULT_JVM = "-default-jvm";
    public static final String DEFAULT_JVM = "--default-jvm";

    /** Flag indicating when a process was restarted. */
    public static final String PROCESS_RESTARTED = "--process-restarted";

    /** Passed in when the host controller is respawned by process controller */
    public static final String RESTART_HOST_CONTROLLER = PROCESS_RESTARTED;

    /** Passed in to a secondary host controller to get a backup of all files on the domain controller" */
    public static final String OLD_BACKUP_DC = "-backup";
    public static final String BACKUP_DC = "--backup";

    /** Passed in to a secondary host controller to attempt to start up using its cached copy of the remote DC, if the remote DC can not be contacted" */
    public static final String OLD_CACHED_DC = "-cached-dc";
    public static final String CACHED_DC = "--cached-dc";

    /** Output usage */
    public static final String OLD_HELP = "-help";
    public static final String HELP = "--help";
    public static final String SHORT_HELP = "-h";

    /** Passed in to a SERVER to configure the git repository used for configuration history. */
    public static final String GIT_REPO = "--git-repo";
    public static final String GIT_BRANCH = "--git-branch";
    public static final String GIT_AUTH = "--git-auth";

    /** Passed in to a DC to choose the domain.xml file. The location must be relative to the domain configuration directory */
    public static final String OLD_DOMAIN_CONFIG = "-domain-config";
    public static final String DOMAIN_CONFIG = "--domain-config";
    public static final String SHORT_DOMAIN_CONFIG = "-c";

    /** Passed in to a DC to choose the domain.xml file. Differs from {@link #DOMAIN_CONFIG} in that the location may be absolute, and
     * the file is not persisted */
    public static final String READ_ONLY_DOMAIN_CONFIG = "--read-only-domain-config";

    /** Passed in to a HC to choose the host.xml file. The location must be relative to the domain configuration directory */
    public static final String OLD_HOST_CONFIG = "-host-config";
    public static final String HOST_CONFIG = "--host-config";

    /** Passed in to a DC to choose the host.xml file. Differs from {@link #HOST_CONFIG} in that the location may be absolute, and
     * the file is not persisted */
    public static final String READ_ONLY_HOST_CONFIG = "--read-only-host-config";

    /** Passed in to a standalone instance to choose the standalone.xml file. The location must be relative to the server configuration directory */
    public static final String SHORT_SERVER_CONFIG = "-c";
    public static final String OLD_SERVER_CONFIG = "-server-config";
    public static final String SERVER_CONFIG = "--server-config";

    /** Passed in to a standalone instance to choose the standalone.xml file. Differs from {@link #SERVER_CONFIG} in that the location may be absolute, and
     * the file is not persisted */
    public static final String READ_ONLY_SERVER_CONFIG = "--read-only-server-config";

    /** Address on which the process controller listens */
    public static final String OLD_PROCESS_CONTROLLER_BIND_ADDR = "-bind-addr";
    public static final String PROCESS_CONTROLLER_BIND_ADDR = "--pc-address";

    /** Port on which the process controller listens */
    public static final String OLD_PROCESS_CONTROLLER_BIND_PORT = "-bind-port";
    public static final String PROCESS_CONTROLLER_BIND_PORT = "--pc-port";

    public static final String SYS_PROP = "-D";
    public static final String SECURITY_PROP = "-S";

    public static final String PUBLIC_BIND_ADDRESS = "-b";

    public static final String DEFAULT_MULTICAST_ADDRESS = "-u";

    public static final String ADMIN_ONLY = "--admin-only";

    public static final String PRIMARY_ADDRESS = "--primary-address";
    public static final String PRIMARY_PORT = "--primary-port";

    public static final String MODULE_PATH = "-mp";
    public static final String SECMGR = "-secmgr";

    public static final String START_MODE = "--start-mode";
    public static final String NORMAL_MODE = "normal";
    public static final String SUSPEND_MODE = "suspend";
    public static final String ADMIN_ONLY_MODE = "admin-only";
    public static final String GRACEFUL_STARTUP = "--graceful-startup";


    // java.net properties
    public static final String PREFER_IPV4_STACK = "java.net.preferIPv4Stack";
    public static final String PREFER_IPV6_ADDRESSES = "java.net.preferIPv6Addresses";

    /** Additional class path items, used only by app client*/
    public static final String APPCLIENT_CONFIG = "--appclient-config";
    public static final String SHORT_HOST = "-H";
    public static final String HOST = "--host";
    public static final String CONNECTION_PROPERTIES = "--ejb-client-properties";


    private CommandLineConstants() {
    }
}
