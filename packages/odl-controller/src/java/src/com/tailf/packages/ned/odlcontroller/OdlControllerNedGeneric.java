package com.tailf.packages.ned.odlcontroller;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import de.odysseus.staxon.json.JsonXMLConfig;
import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLInputFactory;
import de.odysseus.staxon.json.JsonXMLOutputFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;

import java.net.Socket;
import com.tailf.navu.KeyPath2NavuNode;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfList;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfBool;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuNode;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;

import java.io.StringReader;
import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.EnumSet;

import org.apache.log4j.Logger;

import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiInputStream;
import com.tailf.maapi.MaapiSchemas;
import com.tailf.maapi.MaapiSchemas.CSNode;
import com.tailf.maapi.MaapiCursor;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ncs.NcsMain;
import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedEditOp;
import com.tailf.ned.NedException;
import com.tailf.ned.NedGenericBase;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.ned.NedExpectResult;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.maapi.MaapiConfigFlag;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.ned.SSHSession;
import com.tailf.ned.CliSession;
import ch.ethz.ssh2.Connection;

import com.tailf.packages.ned.odlcontroller.namespaces.tailfNedOdlController;

public class OdlControllerNedGeneric extends NedGenericBase  {
    private String      deviceName;
    private InetAddress ip;
    private int         port;
    private String      luser;
    private boolean     trace;
    private int         connectTimeout; // msec
    private int         readTimeout;    // msec
    private int         writeTimeout;   // msec

    private static Logger LOGGER =
           Logger.getLogger(OdlControllerNedGeneric.class);

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE)
    public  Maapi maapi;

    private boolean                  wantReverse=true;

    private String date_string = "2015-07-02";
    private String version_string = "3.0.0";

    NedCapability[]     capabilities;
    NedCapability[]     statsCapas;
    private static MaapiSchemas schemas;
    private String      ruser;
    private String      rpassword;

    public OdlControllerNedGeneric(){
        this(true);
    }

    public OdlControllerNedGeneric(boolean wantReverse){
        this.wantReverse = wantReverse;
    }

    private void maapiStartUserSession(Maapi m) {
        // if the user session doesn't exist, start it
        try {
            m.startUserSession("system",
                    InetAddress.getByName("localhost"),
                    "system",
                    new String[] { "system" },
                    MaapiUserSessionFlag.PROTO_TCP);
        }
        catch (Exception e) {
        }
    }

    public OdlControllerNedGeneric(String deviceName,
                InetAddress ip,
                int port,
                String luser,
                boolean trace,
                int connectTimeout,
                int readTimeout,
                int writeTimeout,
                NedMux mux,
                NedWorker worker,
                boolean wantReverse)  {

        try {
            this.deviceName = deviceName;
            this.ip = ip;
            this.port = port;
            this.luser = luser;
            this.ruser = worker.getRemoteUser();
            this.rpassword = worker.getPassword();
            this.trace = trace;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.writeTimeout = writeTimeout;
            this.wantReverse = wantReverse;

            LOGGER.info("CONNECTING <==");

            this.schemas = Maapi.getSchemas();

            try {
                ResourceManager.registerResources(this);
            } catch (Exception e) {
                LOGGER.error("Error injecting Resources", e);
            }

            this.statsCapas = new NedCapability[0];
            this.capabilities =
                new NedCapability[]{
                new NedCapability("",
                                  "http://tail-f.com/ned/odl-controller",
                                  "tailf-ned-odl-controller",
                                  "",
                                  date_string,
                                  "")
            };

            setConnectionData(this.capabilities,
                              this.statsCapas,
                              wantReverse,
                              TransactionIdMode.UNIQUE_STRING);

            // LOG NCS and NED version & date
            trace(worker, "NCS VERSION: "+Conf.PROTOVSN+" "
                  +String.format("%x", Conf.LIBVSN),"out");

            trace(worker, "NED VERSION: odl-controller "+
                  version_string+" "+date_string, "out");

            LOGGER.info("CONNECTING ==> OK");

        }
        catch (Exception e) {
            worker.error(NedCmd.CONNECT_GENERIC, e.getMessage());
        }
    }


    public String device_id() {
        return deviceName;
    }

    // should return "cli" or "generic"
    public String type() {
        return "generic";
    }

    // Which Yang modules are covered by the class
    public String [] modules() {
        LOGGER.info("modules");
        return new String[] { "tailf-ned-odl-controller" };
    }

    // Which identity is implemented by the class
    public String identity() {
        return "odl-controller-id:odl-controller";
    }

    /**
     * Is invoked by NCS to take the configuration to a new state.
     * We retrive a rev which is a transaction handle to the
     * comming write operation then we write operations towards the device.
     * If all succeded we transition to commit phase or if
     * prepare fails we transition to abort phase.
     *
     * @param w - is the processing worker. It should be used for sending
     * responses to NCS.
     * @param data is the commands for transforming the configuration to
     * a new state.
     */

    public void prepare(NedWorker worker, NedEditOp[] ops)
            throws NedException, IOException {
        LOGGER.info("PREPARE <==");

        edit(worker, ops);
        worker.prepareResponse();
        LOGGER.info("PREPARE ==> OK");
    }

    /**
     * Is invoked by NCS to ask the NED what actions it would take towards
     * the device if it would do a prepare.
     *
     * The NED can send the preformatted output back to NCS through the
     * call to  {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     * prepareDryResponse()}
     *
     * The Ned should invoke the method
     * {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     *   prepareDryResponse()} in <code>NedWorker w</code>
     * when the operation is completed.
     *
     * If the functionality is not supported or an error is detected
     * answer this through a call to
     * {@link com.tailf.ned.NedWorker#error(int,String,String) error()}
     * in <code>NedWorker w</code>.
     *
     * @param w
     *    The NedWorker instance currently responsible for driving the
     *    communication
     *    between NCS and the device. This NedWorker instance should be
     *    used when communicating with the NCS, ie for sending responses,
     *    errors, and trace messages. It is also implements the
     *    {@link NedTracer}
     *    API and can be used in, for example, the {@link SSHSession}
     *    as a tracer.
     *
     * @param ops
     *    Edit operations representing the changes to the configuration.
     */
    public void prepareDry(NedWorker worker, NedEditOp[] ops)
        throws NedException {
        StringBuilder dryRun = new StringBuilder();

        LOGGER.info("PREPARE DRY <==");
        edit(worker, ops, dryRun);
        try {
            worker.prepareDryResponse(dryRun.toString());
        }
        catch (IOException e) {
            throw new NedException("Internal error when calling "+
                                   "prepareDryResponse: "+
                                   e.getMessage());
        }
        LOGGER.info("PREPARE DRY ==> OK");
    }

    public void commit(NedWorker worker, int timeout)
        throws NedException, IOException {
        LOGGER.info("COMMIT <==");
        worker.commitResponse();
        LOGGER.info("COMMIT ==> OK");
    }

    /**
     * Is invoked by NCS to abort the configuration to a previous state.
     *
     * @param w is the processing worker. It should be used for sending
     * responses to NCS. * @param data is the commands for taking the config
     * back to the previous
     * state. */

    public void abort(NedWorker worker , NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("ABORT <==");
        edit(worker, ops);
        worker.abortResponse();
        LOGGER.info("ABORT ==> OK");
    }


    public void revert(NedWorker worker , NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("REVERT <==");
        edit(worker, ops);
        worker.revertResponse();
        LOGGER.info("REVERT ==> OK");
    }


    public void persist(NedWorker worker)
        throws NedException, IOException {
        LOGGER.info("PERSIST <==");
        worker.persistResponse();
        LOGGER.info("PERSIST ==> OK");

    }

    public void close(NedWorker worker)
        throws NedException, IOException {
        close();
    }

    public void close() {
        LOGGER.info("CLOSE <==");
        try {
            if (maapi != null)
                ResourceManager.unregisterResources(this);
        }
        catch (Exception e) {
            ;
        }
        LOGGER.info("CLOSE ==> OK");
    }

    public void trace(NedWorker worker, String msg, String direction) {
        if (trace) {
            worker.trace("-- "+msg+" --\n", direction, device_id());
        }
    }

    /*
     * The generic show command is to
     * grab all configuration from the device and
     * populate the transaction handle  passed to us.
     **/

    public void show(NedWorker worker, int th)
            throws NedException, IOException, Exception {

        LOGGER.info("SHOW <==");
        try {
            maapi.attach(th,-1,1);
        } catch (Exception e)  {
            LOGGER.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC, e.getMessage(), "IO error");
            return;
        }
        try {

        } catch (Exception e)  {
            LOGGER.error("Error:" , e);
            worker.error(NedCmd.SHOW_GENERIC, e.getMessage());
            return;
        }
        worker.showGenericResponse();
        LOGGER.info("SHOW ==> OK");
    }


    /*
     * This method must at-least populate the path it is given,
     * it may do more though
     */

    public void showStats(NedWorker worker, int tHandle, ConfPath path)
        throws NedException, IOException {
        LOGGER.info("SHOWSTATS <== OK");
    }

    /*
     *   This method must at-least fill in all the keys of the list it
     *   is passed, it may do more though. In this example  code we
     *   choose to not let the code in showStatsList() fill in the full
     *   entries, thus forcing an invocation of showStats()
     */

    public void showStatsList(NedWorker worker, int tHandle, ConfPath path)
        throws NedException, IOException {
        LOGGER.info("SHOWSTATSLIST <== OK");
    }

    public boolean isAlive() {
        return true;
    }

    public void reconnect(NedWorker worker) {
        LOGGER.info("RECONNECT <== OK");
    }

    public boolean isConnection(String deviceId,
                                InetAddress ip,
                                int port,
                                String luser,
                                boolean trace,
                                int connectTimeout, // msecs
                                int readTimeout,    // msecs
                                int writeTimeout) { // msecs
        LOGGER.info("ISCONNECTION <== OK");
        return ((this.deviceName.equals(deviceName)) &&
                (this.ip.equals(ip)) &&
                (this.port == port) &&
                (this.luser.equals(luser)) &&
                (this.trace == trace) &&
                (this.connectTimeout == connectTimeout) &&
                (this.readTimeout == readTimeout) &&
                (this.writeTimeout == writeTimeout));
    }

    private String cleanXML(String xml) throws Exception {
        xml = xml.replaceAll("<\\?xml version=\"1.0\" "
                           +"encoding=\"UTF-8\"\\?>", "");
        xml = xml.replaceAll(" xmlns:odl-controller=\""
                           +"http://tail-f.com/ned/odl-controller\"", "");
        xml = xml.replaceAll("odl-controller:", "");
        xml = xml.replaceAll("\n<fragment xmlns=\"http://www.tailf.com\">",
                             "\n<input>");
        xml = xml.replaceAll("\n</fragment>", "\n</input>");
        return xml;
    }

    private String convertXMLtoJSON(String xml)
            throws Exception {

        JsonXMLConfig config = new JsonXMLConfigBuilder()
                                   .autoArray(true)
                                   .autoPrimitive(true)
                                   .prettyPrint(true)
                                   .build();

        StringWriter stringWriter = new StringWriter();
        XMLStreamReader reader = XMLInputFactory.newInstance().
                                 createXMLStreamReader
                                 (new StringReader(cleanXML(xml)));
        XMLStreamWriter writer = new JsonXMLOutputFactory(config).
                                 createXMLStreamWriter(stringWriter);
        TransformerFactory.newInstance().newTransformer().
                           transform(new StAXSource(reader),
                                     new StAXResult(writer));
        return stringWriter.toString();
    }


    private String convertJSONToXML(String json)
            throws Exception {

        JsonXMLConfig config = new JsonXMLConfigBuilder().
                                   prettyPrint(true).
                                   multiplePI(false).
                                   namespaceDeclarations(true).
                                   namespaceMapping("",
                                   "http://tail-f.com/ned/odl-controller").
                                   build();

        StringWriter stringWriter = new StringWriter();
        XMLStreamReader reader = new JsonXMLInputFactory(config).
                                     createXMLStreamReader(
                                     new StringReader(json));
        XMLStreamWriter writer = XMLOutputFactory.newInstance().
                                 createXMLStreamWriter(stringWriter);
        TransformerFactory.newInstance().newTransformer().
                           transform(new StAXSource(reader),
                                     new StAXResult(writer));
        return stringWriter.toString();
    }

    public static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public static String formatErrorOutput(String errorOutput) {
        if (errorOutput.contains("{\"errors\":")) {
            errorOutput = errorOutput.substring(
                          errorOutput.indexOf("{\"errors\":"));
        }
        return errorOutput;
    }

    private String postData(String cmd, String rowData)
            throws NedException, IOException, Exception {

        String url = "http:/" + ip.toString() + ":"
                + port + "/restconf/operations/lfm-mapping-database:" + cmd;

        LOGGER.info("=> POSTDATA: " + rowData+ " " + url);

        HttpURLConnection httpcon = (HttpURLConnection)
                ((new URL(url).openConnection()));

        String userpass = ruser + ":" + rpassword;
        String basicAuth = "Basic " + javax.xml.bind.
                DatatypeConverter.printBase64Binary(userpass.getBytes());
        httpcon.setRequestProperty ("Authorization", basicAuth);

        httpcon.setDoOutput(true);
        httpcon.setRequestMethod("POST");
        httpcon.setRequestProperty("Accept", "application/json");
        httpcon.setRequestProperty("Content-Type", "application/json");
        httpcon.getOutputStream().write(rowData.getBytes("UTF-8"));

        if (httpcon.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new NedException(formatErrorOutput(
                                   convertStreamToString(
                                   httpcon.getErrorStream())));
        }
        String output = convertStreamToString(httpcon.getInputStream());
        httpcon.disconnect();
        return output;
    }


/*
 * If the device has commands, i,e reboot etc that are - just - commands
 * that do not manipulate the configuration, we model those commands
 * in the YANG model, and get invoked here. The task of this code is to
 * look at the input params, invoke the cmd on the device and return
 * data - according to the YANG model
 *
 */
    public void command(NedWorker worker, String cmdname, ConfXMLParam[] p)
        throws NedException, IOException {
        try {
            LOGGER.info("COMMAND <== OK");
            String output = postData(cmdname,
                            convertXMLtoJSON(ConfXMLParam.toXML(p)));
            if (cmdname.indexOf("get-") >= 0) {
                worker.commandResponse(new ConfXMLParam[] {
                        new ConfXMLParamValue(tailfNedOdlController.
                                prefix,
                                tailfNedOdlController._result_,
                        new ConfBuf("Succeeded!")),
                        new ConfXMLParamValue(tailfNedOdlController.
                                prefix,
                                tailfNedOdlController._output_,
                        new ConfBuf(output))
                });
            } else {
                worker.commandResponse(new ConfXMLParam[] {
                        new ConfXMLParamValue(tailfNedOdlController.
                                prefix,
                                tailfNedOdlController._result_,
                                new ConfBuf("Succeeded!")),
                });
            }
        } catch (Exception e) {
            LOGGER.error(e);
            worker.commandResponse(new ConfXMLParam[] {
                    new ConfXMLParamValue(tailfNedOdlController.
                            prefix,
                            tailfNedOdlController._result_,
                            new ConfBuf("Not succeeded: "+e.getMessage()))
            });
            worker.error(NedCmd.CMD, cmdname+" failed:\n" + e.getMessage());
        }
    }

/**
 * Establish a new connection to a device and send response to
 * NCS with information about the device.
 *
 * @param deviceId name of device
 * @param ip address to connect to device
 * @param port port to connect to
 * @param luser name of local NCS user initiating this connection
 * @param trace indicates if trace messages should be generated or not
 * @param connectTimeout in milliseconds
 * @param readTimeout in milliseconds
 * @param writeTimeout in milliseconds
 * @return the connection instance
 **/
    public NedGenericBase newConnection(String deviceId,
                                        InetAddress ip,
                                        int port,
                                        String luser,
                                        boolean trace,
                                        int connectTimeout, // msecs
                                        int readTimeout,    // msecs
                                        int writeTimeout,   // msecs
                                        NedMux mux,
                                        NedWorker worker ) {
        LOGGER.info("newConnection() <==");
        OdlControllerNedGeneric ned = null;

        ned = new OdlControllerNedGeneric(deviceId, ip, port, luser, trace,
                       connectTimeout, readTimeout, writeTimeout,
                       mux, worker,
                       wantReverse );
        LOGGER.info("NED invoking newConnection() ==> OK");
        return ned;
    }

    // Calculate checksum of config
    public void getTransId(NedWorker worker)
        throws Exception, IOException {

        LOGGER.info("GETTRANSID <==");
        // Get configuration
        String res = "";

        // Calculate MD5 checksum
        byte[] bytes = res.getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] thedigest = md.digest(bytes);
        BigInteger md5Number = new BigInteger(1, thedigest);
        String md5String = md5Number.toString(16);

        worker.getTransIdResponse(md5String);
        LOGGER.info("GETTRANSID ==> OK");
    }

    public void edit(NedWorker worker, NedEditOp[] ops)
            throws NedException {
        edit(worker, ops, null);
    }

    public void edit(NedWorker worker, NedEditOp[] ops, StringBuilder dryRun)
            throws NedException {

        try {
            for (int i = 0; i < ops.length; i++) {
                NedEditOp op = ops[i];
                if (op.getOpDone()) {
                    continue;
                }
                switch (op.getOperation()) {
                case NedEditOp.CREATED:
                    // create
                    break;
                case NedEditOp.VALUE_SET:
                    // set
                    break;
                case NedEditOp.DELETED:
                    // delete
                    break;
                }
            }
        } catch (Exception e) {
            throw new NedException("", e);
        }
    }
}
